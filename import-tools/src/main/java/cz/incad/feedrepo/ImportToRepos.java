package cz.incad.feedrepo;

import java.io.*;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.TransformerException;

//import org.fcrepo.client.FedoraContent;
//import org.fcrepo.client.FedoraDatastream;
//import org.fcrepo.client.FedoraException;
//import org.fcrepo.client.FedoraObject;
//import org.fcrepo.client.FedoraRepository;
//import org.fcrepo.client.FedoraResource;
import com.qbizm.kramerius.imp.jaxb.*;
import cz.incad.kramerius.fedora.om.Repository;
import cz.incad.kramerius.fedora.om.RepositoryException;
import cz.incad.kramerius.fedora.om.RepositoryObject;
import cz.incad.kramerius.fedora.om.impl.Fedora4Repository;
import org.apache.commons.io.IOUtils;
import org.fedora.api.ObjectFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.google.inject.AbstractModule;

import cz.incad.kramerius.service.SortingService;
import cz.incad.kramerius.service.impl.IndexerProcessStarter;
import cz.incad.kramerius.utils.RESTHelper;
import cz.incad.kramerius.utils.XMLUtils;
import cz.incad.kramerius.utils.conf.KConfiguration;
import cz.incad.kramerius.utils.pid.LexerException;
import cz.incad.kramerius.utils.pid.PIDParser;

public class ImportToRepos {

    static ObjectFactory of;
    static int counter = 0;
    static final Logger log = Logger.getLogger(ImportToRepos.class.getName());
    static Unmarshaller unmarshaller = null;
    static Marshaller datastreamMarshaller = null;
    static List<String> rootModels = null;

    static SortingService sortingService = null;
    
    static Map<String, List<String>> updateMap = new HashMap<String, List<String>>();
    
    static Repository repo;
    

    static {
        // initialize repo
        try {
            JAXBContext jaxbContext = JAXBContext.newInstance(DigitalObject.class);

            unmarshaller = jaxbContext.createUnmarshaller();

            JAXBContext jaxbdatastreamContext = JAXBContext.newInstance(DatastreamType.class);
            datastreamMarshaller = jaxbdatastreamContext.createMarshaller();

            repo = new Fedora4Repository();
        } catch (Exception e) {
            log.log(Level.SEVERE, "Cannot init JAXB", e);
            throw new RuntimeException(e);
        }

        rootModels = Arrays.asList(KConfiguration.getInstance().getPropertyList("fedora.topLevelModels"));
        if (rootModels == null) {
            rootModels = new ArrayList<String>();
        }
    }

    
    /**
     * @param args
     * @throws UnsupportedEncodingException 
     * @throws RepositoryException
     * @throws IllegalAccessException 
     * @throws InstantiationException 
     * @throws ClassNotFoundException 
     */
    public static void main(String[] args) throws UnsupportedEncodingException, ClassNotFoundException, InstantiationException, IllegalAccessException, RepositoryException {
        System.setProperty("javax.xml.transform.TransformerFactory",
                "com.sun.org.apache.xalan.internal.xsltc.trax.TransformerFactoryImpl");
        String importDirectory = System.getProperties().containsKey("import.directory") ? System.getProperty("import.directory") : KConfiguration.getInstance().getProperty("import.directory");
        ImportToRepos.ingest(KConfiguration.getInstance().getProperty("ingest.url"), KConfiguration.getInstance().getProperty("ingest.user"), KConfiguration.getInstance().getProperty("ingest.password"), importDirectory);
    }
    

    public static void ingest(final String url, final String user, final String pwd, String importRoot) throws UnsupportedEncodingException, ClassNotFoundException, InstantiationException, IllegalAccessException, RepositoryException {
        log.finest("INGEST - url:" + url + " user:" + user + " pwd:" + pwd + " importRoot:" + importRoot);
        
        try {
            // system property 
            String skipIngest = System.getProperties().containsKey("ingest.skip") ? System.getProperty("ingest.skip")
                    : KConfiguration.getInstance().getConfiguration().getString("ingest.skip", "false");
            if (new Boolean(skipIngest)) {
                log.info("INGEST CONFIGURED TO BE SKIPPED, RETURNING");
                return;
            }
            boolean updateExisting = Boolean.valueOf(System.getProperties().containsKey("ingest.updateExisting")
                    ? System.getProperty("ingest.updateExisting")
                    : KConfiguration.getInstance().getConfiguration().getString("ingest.updateExisting", "false"));
            log.info("INGEST updateExisting: " + updateExisting);
            long start = System.currentTimeMillis();
            File importFile = new File(importRoot);
            if (!importFile.exists()) {
                log.severe("Import root folder or control file doesn't exist: " + importFile.getAbsolutePath());
                throw new RuntimeException(
                        "Import root folder or control file doesn't exist: " + importFile.getAbsolutePath());
            }
            initialize(user, pwd);
            //repo.open();
            repo.startTransaction();
            Set<TitlePidTuple> roots = new HashSet<TitlePidTuple>();
            Set<String> sortRelations = new HashSet<String>();
            if (importFile.isDirectory()) {
                visitAllDirsAndFiles(importFile, roots, sortRelations, updateExisting);
            } else {
                BufferedReader reader = null;
                try {
                    reader = new BufferedReader(new FileReader(importFile));
                } catch (FileNotFoundException e) {
                    log.severe("Import file list " + importFile + " not found: " + e);
                    throw new RuntimeException(e);
                }
                try {
                    for (String line; (line = reader.readLine()) != null;) {
                        if ("".equals(line)) {
                            continue;
                        }
                        File importItem = new File(line);
                        if (!importItem.exists()) {
                            log.severe("Import folder doesn't exist: " + importItem.getAbsolutePath());
                            continue;
                        }
                        if (!importItem.isDirectory()) {
                            log.severe("Import item is not a folder: " + importItem.getAbsolutePath());
                            continue;
                        }
                        log.info("Importing " + importItem.getAbsolutePath());
                        visitAllDirsAndFiles(importItem, roots, sortRelations, updateExisting);
                    }
                    reader.close();
                } catch (IOException e) {
                    log.severe("Exception reading import list file: " + e);
                    throw new RuntimeException(e);
                }
            }
            log.info("FINISHED INGESTION IN " + ((System.currentTimeMillis() - start) / 1000.0) + "s, processed "
                    + counter + " files");
            String startSortProperty = System.getProperties().containsKey("ingest.sortRelations")
                    ? System.getProperty("ingest.sortRelations")
                    : KConfiguration.getInstance().getConfiguration().getString("ingest.sortRelations", "true");
            if (new Boolean(startSortProperty)) {

                if (sortRelations.isEmpty()) {
                    log.info("NO MERGED OBJECTS FOR RELATIONS SORTING FOUND.");
                } else {
                    for (String sortPid : sortRelations) {
                        sortingService.sortRelations(sortPid, false);
                    }
                    log.info("ALL MERGED OBJECTS RELATIONS SORTED.");
                }
            } else {
                log.info("RELATIONS SORTING DISABLED.");
            }
            String startIndexerProperty = System.getProperties().containsKey("ingest.startIndexer")
                    ? System.getProperty("ingest.startIndexer")
                    : KConfiguration.getInstance().getConfiguration().getString("ingest.startIndexer", "true");
            if (new Boolean(startIndexerProperty)) {
                if (roots.isEmpty()) {
                    log.info("NO ROOT OBJECTS FOR INDEXING FOUND.");
                } else {
                    StringBuilder pids = new StringBuilder();
                    String pidSeparator = KConfiguration.getInstance().getConfiguration()
                            .getString("indexer.pidSeparator", ";");
                    for (TitlePidTuple tpt : roots) {
                        if (pids.length() > 0) {
                            pids.append(pidSeparator);
                        }
                        pids.append(tpt.pid);
                    }
                    IndexerProcessStarter.spawnIndexer(true, importFile.getName(), pids.toString());
                    log.info("ALL ROOT OBJECTS SCHEDULED FOR INDEXING.");
                }
            } else {
                log.info("AUTO INDEXING DISABLED.");
            }
            repo.commitTransaction();
        } catch(Exception ex) {
            log.log(Level.SEVERE,ex.getMessage(),ex);
            repo.rollbackTransaction();
        } finally {
            //repo.close();
        }
    }

    public static void initialize(final String user, final String pwd) throws ClassNotFoundException, InstantiationException, IllegalAccessException  {
        of = new ObjectFactory();
        repo = new Fedora4Repository();
    }

    private static void visitAllDirsAndFiles(File importFile, Set<TitlePidTuple> roots, Set<String> sortRelations, boolean updateExisting) {
        if (importFile == null) {
            return;
        }
        if (importFile.isDirectory()) {

            File[] children = importFile.listFiles();

            for (File f : children){
                if ("update.list".equalsIgnoreCase(f.getName())){
                    log.info("File update.list detected in folder "+importFile);
                    parseUpdateList(f);
                }
            }

            if (children.length > 1 && children[0].isDirectory()) {//Issue 36
                Arrays.sort(children);
            }
            for (int i = 0; i < children.length; i++) {
                visitAllDirsAndFiles(children[i], roots, sortRelations, updateExisting);
            }
        } else {
            DigitalObject dobj = null;
            try {
                if (!importFile.getName().toLowerCase().endsWith(".xml")) {
                    return;
                }
                Object obj = unmarshaller.unmarshal(importFile);
                dobj = (DigitalObject) obj;
            } catch (Exception e) {
                log.info("Skipping file " + importFile.getName() + " - not an FOXML object. ("+e+")");
                log.log(Level.FINE, "Underlying error was:", e);
                return;
            }
            try {
                if (updateMap.containsKey(dobj.getPID())) {
//                    log.info("Updating datastreams " + updateMap.get(dobj.getPID()) + " in object " + dobj.getPID());
//                    List<DatastreamType> importedDatastreams = dobj.getDatastream();
//                    List<String> datastreamsToUpdate = updateMap.get(dobj.getPID());
//                    for (String dsName : datastreamsToUpdate) {
//                        for (DatastreamType ds : importedDatastreams) {
//                            if (dsName.equalsIgnoreCase(ds.getID())) {
//                                log.info("Updating datastream " + ds.getID());
//                                DatastreamVersionType dsversion = ds.getDatastreamVersion().get(0);
//                                if (dsversion.getXmlContent() != null) {
//                                    Element element = dsversion.getXmlContent().getAny().get(0);
//                                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
//                                    Source xmlSource = new DOMSource(element);
//                                    Result outputTarget = new StreamResult(outputStream);
//                                    try {
//                                        TransformerFactory.newInstance().newTransformer().transform(xmlSource, outputTarget);
//                                    } catch (TransformerException e) {
//                                        throw new RuntimeException(e);
//                                    }
//                                    port.modifyDatastreamByValue(dobj.getPID(), ds.getID(), null, null, null, null, outputStream.toByteArray(), null, null, "Datastream updated by import process", false);
//
//                                } else if (dsversion.getBinaryContent() != null) {
//                                    throw new RuntimeException("Update of managed binary datastream content is not supported.");
//                                } else if (dsversion.getContentLocation() != null) {
//                                    port.purgeDatastream(dobj.getPID(), ds.getID(), null, null, "Datastream updated by import process", false);
//                                    port.addDatastream(dobj.getPID(), ds.getID(), null, null, false, dsversion.getMIMETYPE(), null, dsversion.getContentLocation().getREF(), ds.getCONTROLGROUP(), ds.getSTATE().value(), "DISABLED", null, "Datastream updated by import process");
//                                }
//                            }
//                        }
//                    }
                    if (roots != null) {
                        TitlePidTuple npt = new TitlePidTuple("", dobj.getPID());
                        roots.add(npt);
                        log.info("Added updated object for indexing:" + dobj.getPID());
                    }
                } else {
                    ingest(dobj, dobj.getPID(), sortRelations, roots, updateExisting);
                    checkRoot(dobj, roots);
                }
            }catch (Throwable t){
                log.severe("Error when ingesting PID: "+dobj.getPID()+", "+ t.getMessage());
                throw new RuntimeException(t);
            }
        }
    }

    private static void parseUpdateList(File listFile){
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(listFile));
        } catch (FileNotFoundException e) {
            log.severe("update.list file " + listFile + " not found: " + e);
            throw new RuntimeException(e);
        }
        try {
            for (String line; (line = reader.readLine()) != null;) {
                if ("".equals(line.trim()) || line.trim().startsWith("#")) {
                    continue;
                }
                String[] lineItems = line.split(" ");
                if (lineItems.length<2){
                    continue;
                }
                List<String> streams = new ArrayList<String>(lineItems.length-1);
                for (int i=0;i<lineItems.length-1;i++){
                    if (!"".equals(lineItems[i+1])) {
                        streams.add(lineItems[i + 1]);
                    }
                }
                updateMap.put(lineItems[0], streams);
            }
            reader.close();
        } catch (IOException e) {
            log.severe("Exception reading update.list file: " + e);
            throw new RuntimeException(e);
        }
    }

    public static void ingest(DigitalObject dob, String pid, Set<String> sortRelations, Set<TitlePidTuple> roots, boolean updateExisting) throws IOException, LexerException, TransformerException, RepositoryException {
        long start = System.currentTimeMillis();


        List<PropertyType> properties = dob.getObjectProperties().getProperty();
        for (PropertyType pt :
                properties) {
            String name = pt.getNAME();
            String[] splitted = name.split("#");
            if (splitted.length == 2) {
                //FedoraNamespaces.
            } else {
                log.log(Level.SEVERE, "expecting value size "+splitted.length);
            }
            String value = pt.getVALUE();
        }

        PIDParser pidPArser = new PIDParser(pid);
        pidPArser.objectPid();
        String objId = pidPArser.getObjectId();
        
        RepositoryObject obj = repo.createOrFindObject( objId/*+"?mixin=fedora:object"*/);
        
        List<DatastreamType> datastream = dob.getDatastream();
        for (DatastreamType ds : datastream) {
            String id = ds.getID();
            String controlgroup = ds.getCONTROLGROUP();
            DatastreamVersionType latestDs =  ds.getDatastreamVersion().isEmpty() ? null : ds.getDatastreamVersion().get(ds.getDatastreamVersion().size()-1);
            if (latestDs != null) {
                if (controlgroup.equals("X")) {
                    byte[] xmlContent = xmlContent(latestDs);
                    if (xmlContent != null) {
                        //String s = IOUtils.toString(xmlContent, "UTF-8");
                        createDataStream(repo,obj, id, latestDs,xmlContent);
                    }
                } else if (controlgroup.equals("M")) {
                    byte[] binaryContent = latestDs.getBinaryContent();
                    if (binaryContent != null) {
                        createDataStream(repo, obj, id, latestDs, binaryContent);
                    }
                } else if (controlgroup.equals("E")) {
                    ContentLocationType contentLocation = latestDs.getContentLocation();
                    String ref = contentLocation.getREF();
                    createRelationDataStream(repo, obj, id, ref);
                }
            }
        }
        counter++;
        log.info("Ingested:" + pid + " in " + (System.currentTimeMillis() - start) + "ms, count:" + counter);
    }


    private static void createRelationDataStream(Repository repo, RepositoryObject obj, String id,String url) throws RepositoryException {
        obj.createRedirectedStream(id, url);
    }

    private static void createDataStream(Repository repo, RepositoryObject obj, String id,
                                         DatastreamVersionType versionType, byte[] binaryContent) throws RepositoryException {
        boolean relsExt = id.equals("RELS-EXT");
        String mimeType = relsExt ? "text/xml" : versionType.getMIMETYPE();
        if (relsExt) {
            String model = getModel(versionType);
            if (model != null) {
                obj.setModel(model);
            }
        }

        obj.createStream(id, mimeType, new ByteArrayInputStream(binaryContent));
    }

    public static byte[] xmlContent(DatastreamVersionType dstype) throws TransformerException {
        XmlContentType xmlContent = dstype.getXmlContent();
        List<Element> any = xmlContent.getAny();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(bos);
        XMLUtils.print(any.get(0),ps);
        StringWriter writer = new StringWriter();
        XMLUtils.print(any.get(0),writer);
        System.out.println("'"+writer.toString()+"'");
        return bos.toByteArray();

    }
    
    public static void ingest(File file, String pid, Set<String> sortRelations, Set<TitlePidTuple> roots, boolean updateExisting) throws IOException,  LexerException, TransformerException, RepositoryException {
        Object obj = null;
        if (pid == null) {
            try {
                obj = unmarshaller.unmarshal(file);
                pid = ((DigitalObject) obj).getPID();
            } catch (Exception e) {
                log.info("Skipping file " + file.getName() + " - not an FOXML object.");
                log.log(Level.INFO, "Underlying error was:", e);
                return;
            }
        }
        ingest((DigitalObject)obj, pid, sortRelations, roots, updateExisting);
    }

    public static String getModel(DatastreamVersionType v) {
        XmlContentType dcxml = v.getXmlContent();
        List<Element> elements = dcxml.getAny();
        for (Element el : elements) {
            NodeList types = el.getElementsByTagNameNS("info:fedora/fedora-system:def/model#", "hasModel");
            for (int i = 0; i < types.getLength(); i++) {
                String type = types.item(i).getAttributes().getNamedItemNS("http://www.w3.org/1999/02/22-rdf-syntax-ns#", "resource").getNodeValue();
                if (type.startsWith("info:fedora/model:")) {
                    String model = type.substring(18);//get the string after info:fedora/model:
                    return model;
                }
            }
        }
        return null;
    }

    //TODO: consider what to do
    private static boolean merge(byte[] bytes) {
//        List<RDFTuple> ingested = readRDF(bytes);
//        if (ingested.isEmpty()) {
//            return false;
//        }
//        String pid = ingested.get(0).subject.substring("info:fedora/".length());
//        List<RelationshipTuple> existingWS = port.getRelationships(pid, null);
//        List<RDFTuple> existing = new ArrayList<RDFTuple>(existingWS.size());
//        for (RelationshipTuple t : existingWS) {
//            existing.add(new RDFTuple(t.getSubject(), t.getPredicate(), t.getObject(), t.isIsLiteral()));
//        }
//        ingested.removeAll(existing);
//        boolean touched = false;
//        for (RDFTuple t : ingested) {
//            if (t.object != null) {
//                try {
//                    port.addRelationship(t.subject.substring("info:fedora/".length()), t.predicate, t.object, t.literal, null);
//                    touched = true;
//                } catch (Exception ex) {
//                    log.severe("WARNING- could not add relationship:" + t + "(" + ex + ")");
//                }
//            }
//        }
//        return touched;
        return false;
    }

    private static List<RDFTuple> readRDF(byte[] bytes) {
        XMLInputFactory f = XMLInputFactory.newInstance();
        List<RDFTuple> retval = new ArrayList<RDFTuple>();
        String subject = null;
        boolean inRdf = false;
        try {
            XMLStreamReader r = f.createXMLStreamReader(new ByteArrayInputStream(bytes));
            while (r.hasNext()) {
                r.next();
                if (r.isStartElement()) {
                    if ("rdf".equals(r.getName().getPrefix()) && "Description".equals(r.getName().getLocalPart())) {
                        subject = r.getAttributeValue(r.getNamespaceURI("rdf"), "about");
                        inRdf = true;
                        continue;
                    }
                    if (inRdf) {
                        String predicate = r.getName().getNamespaceURI() + r.getName().getLocalPart();
                        String object = r.getAttributeValue(r.getNamespaceURI("rdf"), "resource");
                        boolean literal = false;
                        if (object == null){
                            object = r.getElementText();
                            if (object != null){
                                literal = true;
                            }
                        }
                        retval.add(new RDFTuple(subject, predicate, object, literal));
                    }
                }
                if (r.isEndElement()) {
                    if ("rdf".equals(r.getName().getPrefix()) && "Description".equals(r.getName().getLocalPart())) {
                        inRdf = false;
                    }
                }
            }
        } catch (XMLStreamException ex) {
            ex.printStackTrace();
        }
        return retval;
    }

   /* public static void main TestReadRDF (String[] args){
        try{
            File file = new File("/Work/Kramerius/data/prvnidavka-converted/40114/0eaa6730-9068-11dd-97de-000d606f5dc6.xml");
            FileInputStream is = new FileInputStream(file);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            IOUtils.copyStreams(is, bos);
            byte[] bytes = bos.toByteArray();
            List<RDFTuple> rdf = readRDF(bytes);
            System.out.print(rdf);
        }catch(Throwable th){
            System.out.print(th);
        }
    }*/

    /**
     * Parse FOXML file and if it has model in fedora.topLevelModels, add its
     * PID to roots list. Objects in the roots list then will be submitted to
     * indexer
     */
    private static void checkRoot(DigitalObject dobj, Set<TitlePidTuple> roots) {
        try {

            boolean isRootObject = false;
            String title = "";
            for (DatastreamType ds : dobj.getDatastream()) {
                if ("DC".equals(ds.getID())) {//obtain title from DC stream
                    List<DatastreamVersionType> versions = ds.getDatastreamVersion();
                    if (versions != null) {
                        DatastreamVersionType v = versions.get(versions.size() - 1);
                        XmlContentType dcxml = v.getXmlContent();
                        List<Element> elements = dcxml.getAny();
                        for (Element el : elements) {
                            NodeList titles = el.getElementsByTagNameNS("http://purl.org/dc/elements/1.1/", "title");
                            if (titles.getLength() > 0) {
                                title = titles.item(0).getTextContent();
                            }
                        }
                    }
                }
                if ("RELS-EXT".equals(ds.getID())) { //check for root model in RELS-EXT
                    List<DatastreamVersionType> versions = ds.getDatastreamVersion();
                    if (versions != null) {
                        DatastreamVersionType v = versions.get(versions.size() - 1);
                        XmlContentType dcxml = v.getXmlContent();
                        List<Element> elements = dcxml.getAny();
                        for (Element el : elements) {
                            NodeList types = el.getElementsByTagNameNS("info:fedora/fedora-system:def/model#", "hasModel");
                            for (int i = 0; i < types.getLength(); i++) {
                                String type = types.item(i).getAttributes().getNamedItemNS("http://www.w3.org/1999/02/22-rdf-syntax-ns#", "resource").getNodeValue();
                                if (type.startsWith("info:fedora/model:")) {
                                    String model = type.substring(18);//get the string after info:fedora/model:
                                    isRootObject = rootModels.contains(model);
                                }
                            }
                        }
                    }
                }

            }
            if (isRootObject) {
                TitlePidTuple npt = new TitlePidTuple(title, dobj.getPID());
                if(roots!= null){
                    roots.add(npt);
                    log.info("Found object for indexing - " + npt);
                }
            }

        } catch (Exception ex) {
            log.log(Level.WARNING, "Error in Ingest.checkRoot for file " + dobj.getPID() + ", file cannot be checked for auto-indexing : " + ex);
        }
    }

    /**
     * Checks if fedora contains object with given PID
     *
     * @param pid requested PID
     * @return true if given object exists
     */
    public static boolean objectExists(String pid) {
        try {
            String fedoraObjectURL = KConfiguration.getInstance().getFedoraHost() + "/get/" + pid;
            URLConnection urlcon = RESTHelper.openConnection(fedoraObjectURL, KConfiguration.getInstance().getFedoraUser(), KConfiguration.getInstance().getFedoraPass());
            urlcon.connect();
            Object target = urlcon.getContent();
            if (target != null) {
                return true;
            }
        } catch (Exception ex) {
            return false;
        }
        return false;
    }
}

class RDFTuple {

    String subject;
    String predicate;
    String object;
    boolean literal;

    public RDFTuple(String subject, String predicate, String object, boolean literal) {
        super();
        this.subject = subject;
        this.predicate = predicate;
        this.object = object;
        this.literal = literal;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        RDFTuple rdfTuple = (RDFTuple) o;

        if (literal != rdfTuple.literal) return false;
        if (object != null ? !object.equals(rdfTuple.object) : rdfTuple.object != null) return false;
        if (predicate != null ? !predicate.equals(rdfTuple.predicate) : rdfTuple.predicate != null) return false;
        if (subject != null ? !subject.equals(rdfTuple.subject) : rdfTuple.subject != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = subject != null ? subject.hashCode() : 0;
        result = 31 * result + (predicate != null ? predicate.hashCode() : 0);
        result = 31 * result + (object != null ? object.hashCode() : 0);
        result = 31 * result + (literal ? 1 : 0);
        return result;
    }

    @Override
    public String toString() {
        return "RDFTuple{" +
                "subject='" + subject + '\'' +
                ", predicate='" + predicate + '\'' +
                ", object='" + object + '\'' +
                ", literal=" + literal +
                '}';
    }
}

class TitlePidTuple {

    public String title;
    public String pid;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TitlePidTuple that = (TitlePidTuple) o;

        if (pid != null ? !pid.equals(that.pid) : that.pid != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return pid != null ? pid.hashCode() : 0;
    }

    public TitlePidTuple(String name, String pid) {
        this.title = name;
        this.pid = pid;
    }

    @Override
    public String toString() {
        return "Title:" + title + " PID:" + pid;
    }
}



class ImportModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(KConfiguration.class).toInstance(KConfiguration.getInstance());
    }
}