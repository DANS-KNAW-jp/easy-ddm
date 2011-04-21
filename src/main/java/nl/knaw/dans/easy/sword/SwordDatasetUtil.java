package nl.knaw.dans.easy.sword;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import nl.knaw.dans.common.jibx.JiBXObjectFactory;
import nl.knaw.dans.common.lang.service.exceptions.ServiceException;
import nl.knaw.dans.common.lang.xml.SchemaCreationException;
import nl.knaw.dans.common.lang.xml.ValidatorException;
import nl.knaw.dans.common.lang.xml.XMLDeserializationException;
import nl.knaw.dans.common.lang.xml.XMLErrorHandler;
import nl.knaw.dans.common.lang.xml.XMLErrorHandler.Reporter;
import nl.knaw.dans.easy.business.dataset.DatasetSubmissionImpl;
import nl.knaw.dans.easy.domain.authn.Authentication.State;
import nl.knaw.dans.easy.domain.authn.UsernamePasswordAuthentication;
import nl.knaw.dans.easy.domain.dataset.DatasetImpl;
import nl.knaw.dans.easy.domain.form.FormDefinition;
import nl.knaw.dans.easy.domain.model.Dataset;
import nl.knaw.dans.easy.domain.model.emd.EasyMetadata;
import nl.knaw.dans.easy.domain.model.emd.EasyMetadataImpl;
import nl.knaw.dans.easy.domain.model.emd.EasyMetadataValidator;
import nl.knaw.dans.easy.domain.model.emd.types.ApplicationSpecific.MetadataFormat;
import nl.knaw.dans.easy.domain.model.emd.types.BasicString;
import nl.knaw.dans.easy.domain.model.user.EasyUser;
import nl.knaw.dans.easy.domain.worker.WorkReporter;
import nl.knaw.dans.easy.servicelayer.services.ItemService;
import nl.knaw.dans.easy.servicelayer.services.Services;

import org.purl.sword.base.ErrorCodes;
import org.purl.sword.base.SWORDAuthenticationException;
import org.purl.sword.base.SWORDException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

/**
 * Wrapper for the easy business API
 */
public class SwordDatasetUtil
{
    public static final String DEFAULT_EMD_VERSION = EasyMetadataValidator.VERSION_0_1;

    private static Logger      log                 = LoggerFactory.getLogger(SwordDatasetUtil.class);

    /**
     * Gets an authenticated user.
     * 
     * @param userID
     * @param password
     * @return
     * @throws SWORDAuthenticationException
     *         if required services are not available
     * @throws SWORDException
     *         if the user can not be authenticated
     */
    public static EasyUser getUser(final String userID, final String password) throws SWORDAuthenticationException, SWORDException
    {
        final UsernamePasswordAuthentication authentication = new UsernamePasswordAuthentication(userID, password);
        try
        {
            Services.getUserService().authenticate(authentication);
        }
        catch (final ServiceException exception)
        {
            throw newSwordException(userID + " authentication problem", exception);
        }
        if (authentication.getState() == State.NotAuthenticated)
            return null;
        return authentication.getUser();
    }

    /**
     * Submits a new dataset.
     * 
     * @param user
     *        the owner of the new dataset
     * @param easyMetadata
     *        the metadata for the new dataset,
     * @param directory
     *        a directory containing the files for the new dataset
     * @param fileList
     *        the list of files in the directory to add to the new dataset
     * @param workListeners
     * @return
     * @throws SWORDException
     */
    public static Dataset submitNewDataset(final EasyUser user, final byte[] easyMetadata, final File directory, final List<File> fileList)
            throws SWORDException
    {
        validateEasyMetadata(easyMetadata);

        final EasyMetadata metadata = unmarshallEasyMetaData(easyMetadata);
        final MetadataFormat mdFormat = metadata.getEmdOther().getEasApplicationSpecific().getMetadataFormat();
        final Dataset dataset = createEmptyDataset(mdFormat);

        enhanceWithDefaults(metadata, dataset);
        ((DatasetImpl) dataset).setEasyMetadata(metadata);

        dataset.setOwnerId(user.getId());
        dataset.getAdministrativeMetadata().setDepositor(user);

        ingestFiles(user, dataset, directory, fileList);
        submit(user, dataset);

        return dataset;
    }

    /**
     * Enhances the custom metadata with defaults present in the metadata of the dataset.
     * 
     * @param metadata
     *        the custom metadata
     * @param dataset
     *        containing default metadata for a specific format
     * @return the custom metadata
     */
    private static EasyMetadata enhanceWithDefaults(final EasyMetadata metadata, final Dataset dataset)
    {
        final List<BasicString> audienceList = dataset.getEasyMetadata().getEmdAudience().getTermsAudience();
        if (!audienceList.isEmpty())
        {
            metadata.getEmdAudience().getTermsAudience().add(audienceList.get(0));
        }
        return metadata;
    }

    /** Just a wrapper for exceptions. */
    private static void submit(final EasyUser user, final Dataset dataset) throws SWORDException
    {
        // TODO don't skip validation of metadata
        // FormDefinition is designed to report errors to the web GUI, but we are no GUI
        final DatasetSubmissionImpl submission = new DatasetSubmissionImpl(new FormDefinition("dummy"), dataset, user);
        final MyReporter reporter = new MyReporter("problem submitting " + dataset.getStoreId() + " by " + user, "problem with submitting");

        try
        {
            log.debug("before Services.getDatasetService().submitDataset for " + dataset.getStoreId());
            Services.getDatasetService().submitDataset(submission, reporter);
            reporter.checkOK();
            log.debug("after Services.getDatasetService().submitDataset for " + dataset.getStoreId());
        }
        catch (final ServiceException exception)
        {
            throw newSwordException("Dataset created but submission failed " + dataset.getStoreId() + " " + user.getId(), exception);
        }
    }

    /** Just a wrapper for exceptions. */
    private static void ingestFiles(final EasyUser user, final Dataset dataset, final File tempDirectory, final List<File> fileList) throws SWORDException
    {
        final String storeId = dataset.getStoreId();
        try
        {
            final ItemService itemService = Services.getItemService();
            final String message = "ingesting files from " + tempDirectory + " into " + dataset.getStoreId() + " " + Arrays.deepToString(fileList.toArray());
            final MyReporter reporter = new MyReporter("problem " + message, "ingesting files");
            log.debug(message);
            
            itemService.addDirectoryContents(user, dataset, storeId, tempDirectory, fileList, reporter);
            
            final int size = itemService.getFilesAndFolders(user, dataset, storeId, -1, -1, null, null).size();
            log.debug("addFiles =========================" + //
                    " workStarted: " + reporter.workStarted + //
                    " IngestedObjectCount: " + reporter.getIngestedObjectCount() + //
                    " workEnded: " + reporter.workEnded + //
                    " exceptions: " + reporter.reportedExceptions.size() + //
                    " folder count: " + dataset.getChildFolderCount() + //
                    " itemService files: " + size);
            reporter.checkOK();
            if (size < fileList.size())
                throw newSwordException(String.format("Added only %d files of %d to %s", size, fileList.size(), dataset.getStoreId()), null);
        }
        catch (final ServiceException exception)
        {
            throw newSwordException("Can't add files to the new dataset " + storeId + " " + user.getId(), exception);
        }
    }

    private static class MyReporter extends WorkReporter
    {

        List<Throwable>      reportedExceptions = new ArrayList<Throwable>();
        boolean              workStarted;
        boolean              workEnded;
        private final String message;
        private final String messageForClient;

        MyReporter(final String message, final String messageForClient)
        {
            this.message = message;
            this.messageForClient = messageForClient;
        }

        @Override
        public void onException(Throwable t)
        {
            super.onException(t);
            log.error(message, t);
            reportedExceptions.add(t);
        }

        @Override
        public boolean onWorkStart()
        {
            workStarted = true;
            return super.onWorkStart();
        }

        @Override
        public void onWorkEnd()
        {
            workEnded = true;
            super.onWorkEnd();
        }
        public void checkOK () throws SWORDException {
            if (reportedExceptions.size()>0||!workStarted||!workEnded)
                throw newSwordException("Dataset created but problem with "+messageForClient,null);
        }
    }

    /** Just a wrapper for exceptions. */
    private static Dataset createEmptyDataset(final MetadataFormat metadataFormat) throws SWORDException
    {
        final Dataset dataset;
        try
        {
            dataset = Services.getDatasetService().newDataset(metadataFormat);
        }
        catch (final ServiceException exception)
        {
            throw newSwordException("Can't create a new dataset " + metadataFormat, exception);
        }
        return dataset;
    }

    /** Just a wrapper for exceptions. */
    private static EasyMetadata unmarshallEasyMetaData(final byte[] data) throws SWORDException
    {

        final EasyMetadata metadata;
        try
        {
            metadata = (EasyMetadata) JiBXObjectFactory.unmarshal(EasyMetadataImpl.class, data);
        }
        catch (final XMLDeserializationException exception)
        {
            throw newSwordException("EASY metadata unmarshall exception", exception);
        }
        return metadata;
    }

    /** Just a wrapper for exceptions. */
    private static void validateEasyMetadata(final byte[] data) throws SWORDException
    {
        final XMLErrorHandler handler = new XMLErrorHandler(Reporter.off);
        try
        {
            EasyMetadataValidator.instance().validate(handler, new String(data, "UTF-8"), DEFAULT_EMD_VERSION);
        }
        catch (final ValidatorException exception)
        {
            throw newSwordException("EASY metadata validation exception", exception);
        }
        catch (final UnsupportedEncodingException exception)
        {
            throw newSwordException("EASY metadata validation exception", exception);
        }
        catch (final SAXException exception)
        {
            throw newSwordException("EASY metadata validation exception", exception);
        }
        catch (final SchemaCreationException exception)
        {
            throw newSwordException("EASY metadata validation exception", exception);
        }
        if (!handler.passed())
            throw new SWORDException("Invalid EASY metadata: \n" + handler.getMessages(), null, ErrorCodes.ERROR_BAD_REQUEST);
    }

    private static SWORDException newSwordException(final String message, final Exception exception)
    {
        log.error(message, exception);
        return new SWORDException(message);
    }
}
