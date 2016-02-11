package nl.knaw.dans.easy.web.template.emd.atomic;

import java.io.File;

import nl.knaw.dans.common.lang.repo.DmoStoreId;
import nl.knaw.dans.easy.EasyApplicationContextMock;
import nl.knaw.dans.easy.EasyWicketTester;
import nl.knaw.dans.easy.FileStoreMocker;
import nl.knaw.dans.easy.TestUtil;
import nl.knaw.dans.easy.data.Data;
import nl.knaw.dans.easy.domain.dataset.DatasetImpl;
import nl.knaw.dans.easy.domain.model.AccessibleTo;
import nl.knaw.dans.easy.domain.model.Dataset;
import nl.knaw.dans.easy.domain.model.FolderItem;
import nl.knaw.dans.easy.domain.model.VisibleTo;
import nl.knaw.dans.easy.domain.model.user.CreatorRole;
import nl.knaw.dans.easy.web.common.DatasetModel;

import org.apache.commons.io.FileUtils;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.util.tester.ITestPanelSource;
import org.apache.wicket.util.tester.WicketTester;
import org.junit.After;
import org.junit.Ignore;
import org.junit.Test;
import org.powermock.api.easymock.PowerMock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DepositUploadPanelTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(DepositUploadPanelTest.class);

    @After
    public void cleanup() {
        TestUtil.cleanup();
    }

    @Test
    public void smokeTest() throws Exception {
        String datasetId = new DmoStoreId(Dataset.NAMESPACE, "1").getStoreId();
        final Dataset dataset = new DatasetImpl(datasetId);

        final FileStoreMocker fileStoreMocker = initFileStoreMocker(dataset);
        new Data().setFileStoreAccess(fileStoreMocker.getFileStoreAccess());

        final EasyApplicationContextMock applicationContextMock = new EasyApplicationContextMock();
        applicationContextMock.expectStandardSecurity();
        applicationContextMock.expectNoAudioVideoFiles();
        applicationContextMock.putBean("fileStoreAccess", fileStoreMocker.getFileStoreAccess());

        PowerMock.replayAll();

        final WicketTester tester = EasyWicketTester.create(applicationContextMock);
        tester.getApplication().getResourceSettings().addResourceFolder("src/main/java/");
        tester.startPanel(new ITestPanelSource() {
            private static final long serialVersionUID = 1L;

            @Override
            public Panel getTestPanel(final String panelId) {
                return new DepositUploadPanel(panelId, new DatasetModel(dataset));
            }
        });
        tester.debugComponentTrees();
        tester.assertVisible("panel:uploadPanel:uploadIframe");
        tester.assertVisible("panel:uploadPanel:uploadProgress");
        FileUtils.write(new File("target/DepositUploadPanel-smokeTest.html"), tester.getServletResponse().getDocument());
        // How to get into the IFrame to hit the submit button?

        // rendered as test:
        // src="?wicket:interface=:1:panel:uploadPanel:uploadIframe::ILinkListener::"
        // in situ:
        // src="?wicket:interface=:2:depositPanel:depositForm:recursivePanel:levelContainer:recursivePanelContainer:recursivePanels:6:recursivePanel:customPanel:uploadPanel:uploadPanel:uploadIframe::ILinkListener::"

        // FormTester formTester = iframeTester.newFormTester("uploadForm");
        // formTester.setValue("file", ACCENT_XML);
        // formTester.setValue("uploadId", "123");
        // formTester.submit();
        fileStoreMocker.close();
    }

    private FileStoreMocker initFileStoreMocker(final Dataset dataset) throws Exception {
        final FileStoreMocker fileStoreMocker = new FileStoreMocker();
        fileStoreMocker.insertRootFolder(dataset);
        final FolderItem folder = fileStoreMocker.insertFolder(1, dataset, "a");
        fileStoreMocker.insertFile(1, folder, "a/x.y", CreatorRole.DEPOSITOR, VisibleTo.RESTRICTED_REQUEST, AccessibleTo.RESTRICTED_REQUEST);
        fileStoreMocker.logContent(LOGGER);
        return fileStoreMocker;
    }
}
