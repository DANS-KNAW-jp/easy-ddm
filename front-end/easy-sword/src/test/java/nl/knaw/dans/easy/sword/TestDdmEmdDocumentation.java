package nl.knaw.dans.easy.sword;

import nl.knaw.dans.common.jibx.JiBXObjectFactory;
import nl.knaw.dans.common.lang.HomeDirectory;
import nl.knaw.dans.common.lang.ResourceLocator;
import nl.knaw.dans.common.lang.repo.DmoStoreId;
import nl.knaw.dans.common.lang.service.exceptions.ServiceException;
import nl.knaw.dans.common.lang.util.StreamUtil;
import nl.knaw.dans.easy.business.services.EasyDisciplineCollectionService;
import nl.knaw.dans.easy.domain.deposit.discipline.DepositDiscipline;
import nl.knaw.dans.easy.domain.deposit.discipline.DisciplineImpl;
import nl.knaw.dans.easy.domain.form.FormDescriptor;
import nl.knaw.dans.easy.domain.form.FormDescriptorLoader;
import nl.knaw.dans.easy.domain.model.Dataset;
import nl.knaw.dans.easy.domain.model.disciplinecollection.DisciplineContainer;
import nl.knaw.dans.easy.domain.model.user.EasyUser;
import nl.knaw.dans.easy.servicelayer.LicenseComposer;
import nl.knaw.dans.easy.servicelayer.services.DepositService;
import nl.knaw.dans.easy.servicelayer.services.DisciplineCollectionService;
import nl.knaw.dans.easy.servicelayer.services.Services;
import nl.knaw.dans.pf.language.ddm.api.DDMValidator;
import nl.knaw.dans.pf.language.ddm.api.Ddm2EmdCrosswalk;
import nl.knaw.dans.pf.language.ddm.api.Ddm2EmdHandlerMap;
import nl.knaw.dans.pf.language.ddm.handlermaps.NameSpace;
import nl.knaw.dans.pf.language.emd.EasyMetadata;
import nl.knaw.dans.pf.language.emd.binding.EmdMarshaller;
import nl.knaw.dans.pf.language.emd.types.ApplicationSpecific;
import nl.knaw.dans.pf.language.xml.binding.Encoding;
import nl.knaw.dans.pf.language.xml.validation.XMLErrorHandler;
import nl.knaw.dans.pf.language.xml.vocabulary.MapFromXSD;
import org.dom4j.Document;
import org.dom4j.io.SAXReader;
import org.easymock.EasyMock;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXParseException;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.apache.commons.io.FileUtils.deleteQuietly;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.isA;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assume.assumeTrue;
import static org.powermock.api.easymock.PowerMock.createMock;
import static org.powermock.api.easymock.PowerMock.replayAll;

/** Shows how/which DDM fields appear in a license document as meta data */
public class TestDdmEmdDocumentation {
    public static final String SCHEMAS = "http://easy.dans.knaw.nl/schemas";
    private static final File INPUT = new File("src/test/resources/input/demoDDM.xml");
    private static final File OUTPUT = new File("target/demoDDM");

    private static final Ddm2EmdCrosswalk crosswalker = new Ddm2EmdCrosswalk(new DDMValidator());
    private static final EasyUser MOCKED_DEPOSITOR = EasyMock.createMock(EasyUser.class);
    private static final Logger logger = LoggerFactory.getLogger(TestDdmEmdDocumentation.class);

    @BeforeClass
    public static void init() throws Exception {
        assumeTrue("can access " + SCHEMAS, canConnect(SCHEMAS));
    }

    private static boolean canConnect(String url) {
        try {
            HttpURLConnection urlConnection = (HttpURLConnection) new URL(url).openConnection();
            urlConnection.connect();
            urlConnection.disconnect();
            return true;
        }
        catch (IOException e) {
            return false;
        }
    }

    @Test
    public void ddm2emd() throws Exception {

        expectDepositDisciplines();
        replayAll();

        deleteQuietly(OUTPUT); // avoid remnants of a previous execution
        OUTPUT.mkdirs(); // not cleaned up as a secondary purpose is generating documentation

        XMLErrorHandler handler = new DDMValidator().validate(INPUT);
        assertThat(handler.getErrors().size(), is(0));
        assertThat(handler.getFatalErrors().size(), is(0));
        assertThat(handler.getWarnings().size(), is(0));

        // TODO read https://easy.dans.knaw.nl/schemas/docs/examples/ddm/example1.xml
        final EasyMetadata emd = crosswalker.createFrom(readFile(INPUT));

        logger.info(crosswalker.getXmlErrorHandler().getMessages());
        writeFile(new File(OUTPUT, "emd.xml"), new EmdMarshaller(emd).getXmlString());
        writeFile(new File(OUTPUT, "emd.html"), getMetadataAsHTML(emd));

        assertThat(crosswalker.getXmlErrorHandler().getErrors().size(), is(0));
        assertThat(crosswalker.getXmlErrorHandler().getFatalErrors().size(), is(0));
        List<SAXParseException> warnings = crosswalker.getXmlErrorHandler().getWarnings();
        assertThat(warnings.size(), is(12));
        assertThat(warnings.get(0).getMessage(), is("skipped http://purl.org/dc/terms/ accessRights [not yet configured/implemented]"));
        assertThat(warnings.get(11).getMessage(), is("skipped mods:recordOrigin at level:4"));
    }

    private void expectDepositDisciplines() throws Exception {
        new Services().setDepositService(createMock(DepositService.class));
        final List<DepositDiscipline> list = new ArrayList<DepositDiscipline>();
        for (final ApplicationSpecific.MetadataFormat mdFormat : ApplicationSpecific.MetadataFormat.values()) {
            list.add(loadDiscipline(mdFormat));
        }
        expect(Services.getDepositService().getDisciplines()).andStubReturn(list);
    }

    private DisciplineImpl loadDiscipline(final ApplicationSpecific.MetadataFormat mdFormat) throws Exception {

        final String location = FormDescriptorLoader.FORM_DESCRIPTIONS + mdFormat.name().toLowerCase() + ".xml";
        final FileInputStream stream = new FileInputStream("../../lib/easy-business/src/main/java/nl/knaw/dans/easy/domain/form/" + location);
        final FormDescriptor formDescriptor = (FormDescriptor) JiBXObjectFactory.unmarshal(FormDescriptor.class, stream);
        final DisciplineImpl discipline = new DisciplineImpl(formDescriptor);
        expect(Services.getDepositService().getDiscipline(mdFormat)).andStubReturn(discipline);;
        return discipline;
    }

    private String getMetadataAsHTML(final EasyMetadata emd) throws Exception {
        new Services().setDisciplineService(mockDisciplineService());

        // ironically the HomeDir is required because the constructor of the LicenseComposer
        // indirectly calls ResourceLocator.getFile for snippets not used by formatMetaData
        mockHomeDir();

        // get just a section of the license document
        // otherwise we have to mock files and more Dataset properties
        return new LicenseComposer(MOCKED_DEPOSITOR, mockDataset(emd), true) {
            String getMetadataAsHTML() throws Exception {
                // no import because of conflicts with dom4j
                final com.lowagie.text.Document document = new com.lowagie.text.Document();
                final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                com.lowagie.text.html.HtmlWriter.getInstance(document, outputStream);
                document.open();
                document.add(formatMetaData(document));
                document.close();
                return outputStream.toString();
            }
        }.getMetadataAsHTML();
    }

    @Test
    public void handlerMapCoverage() throws Exception {
        final Document document = new SAXReader().read(INPUT);
        for (final String key : Ddm2EmdHandlerMap.getInstance().getKeys()) {
            final String[] split = key.split("/");
            final String tag = split[1];
            final String localNameOfType = split[0];
            final String xpath;
            if (localNameOfType.length() == 0)
                xpath = "//" + tag;
            else
                xpath = "//" + tag + "[contains(@xsi:type,'" + localNameOfType + "')]";
            if (tag.equals("ddm:additional-xml"))
                ;
            else {
                final String msg = "<" + tag + "> with attribute xsi:type " + localNameOfType + " not in xml";
                // assertTrue(msg, document.selectNodes(xpath).size() > 0);
            }
        }
    }

    private Dataset mockDataset(final EasyMetadata emd) throws Exception {
        final Dataset dataset = EasyMock.createMock(Dataset.class);
        expect(dataset.getEasyMetadata()).andStubReturn(emd);
        EasyMock.replay(dataset);
        return dataset;
    }

    private static DisciplineCollectionService mockDisciplineService() throws Exception {
        final Map<String, String> disciplines = new MapFromXSD(NameSpace.NARCIS_TYPE.xsd).getAppInfo2doc();
        return new EasyDisciplineCollectionService() {
            public DisciplineContainer getDisciplineById(final DmoStoreId dmoStoreId) {
                final String key = dmoStoreId.toString();
                final String value = disciplines.get(key);
                final DisciplineContainer container = EasyMock.createMock(DisciplineContainer.class);
                expect(container.getName()).andStubReturn(value == null ? key : value);
                EasyMock.replay(container);
                return container;

            }
        };
    }

    private String readFile(final File file) throws Exception {
        final byte[] xml = StreamUtil.getBytes(new FileInputStream(file));
        return new String(xml, Encoding.UTF8);
    }

    private void writeFile(final File file, final String string) throws Exception {
        final FileOutputStream os = new FileOutputStream(file);
        try {
            os.write(string.getBytes());
        }
        finally {
            os.close();
        }
    }

    public static void mockHomeDir() {
        // this hack is only possible because both projects are in the same git repository
        final String home = "../easy-webui/src/main/assembly/dist";

        final HomeDirectory homeDirectory = new HomeDirectory() {
            @Override
            public File getHomeDirectory() {
                return new File(home);
            }

            @Override
            public String getHome() {
                return home;
            }
        };
        new ResourceLocator(homeDirectory);
    }
}