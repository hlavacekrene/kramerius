package cz.incad.kramerius.imaging.impl;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.antlr.stringtemplate.StringTemplate;
import org.antlr.stringtemplate.StringTemplateGroup;
import org.antlr.stringtemplate.language.DefaultTemplateLexer;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.Text;

import com.google.inject.Inject;
import com.google.inject.name.Named;

import cz.incad.kramerius.FedoraAccess;
import cz.incad.kramerius.FedoraNamespaceContext;
import cz.incad.kramerius.imaging.DiscStrucutreForStore;
import cz.incad.kramerius.imaging.paths.Path;
import cz.incad.kramerius.imaging.paths.impl.DirPathImpl;
import cz.incad.kramerius.utils.FedoraUtils;
import cz.incad.kramerius.utils.IOUtils;

/**
 * Manage same structure for storing objects as fedora 3.<br>  
 * Need connection provider  to fedora 3 database
 * @author pavels
 */
public class Fedora3StreamsDiscStructure implements DiscStrucutreForStore {
    
    static java.util.logging.Logger LOGGER = java.util.logging.Logger.getLogger(Fedora3StreamsDiscStructure.class.getName());


    @Named("securedFedoraAccess")
    private FedoraAccess fedoraAccess;


    
    @Inject
    public Fedora3StreamsDiscStructure(@Named("securedFedoraAccess") FedoraAccess fedoraAccess) throws IOException {
        super();
        this.fedoraAccess = fedoraAccess;
    }

    

    @Override
    public Path getUUIDFile(String uuid,  String rootPath) throws IOException {
        try {
            Date dateFromFedora = this.fedoraAccess.getStreamLastmodifiedFlag("uuid:"+uuid, FedoraUtils.IMG_FULL_STREAM);
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(dateFromFedora);
            
            List<String> relativeDataStreamPath = Arrays.asList(
                    ""+calendar.get(Calendar.YEAR),
                    ""+calendar.get(Calendar.MONTH),
                    ""+calendar.get(Calendar.DAY_OF_MONTH),
                    ""+calendar.get(Calendar.HOUR),
                    ""+calendar.get(Calendar.MINUTE)
            );
                    
            File rootDir = new File(rootPath);
            if (!rootDir.exists()) {
                if (!rootDir.mkdirs()) {
                    throw new IOException("cannot create dir '" + rootPath + "'");
                }
            }
            StringTemplate template = new StringTemplate("$files;separator=sep$$sep$$uuid$");
            template.setAttribute("files", relativeDataStreamPath);
            template.setAttribute("sep", File.separator);
            template.setAttribute("uuid", uuid);
            String filePath = template.toString();

            return new DirPathImpl(new File(rootDir, filePath), null);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, e.getMessage(),e);
            throw new IOException(e);
        }
    }
    
    

    public static Date disectCreateDate(String data) throws DatatypeConfigurationException {
        XMLGregorianCalendar gregorianCalendar = DatatypeFactory.newInstance().newXMLGregorianCalendar(data);
        int year = gregorianCalendar.getYear();
        int day = gregorianCalendar.getDay();
        int month = gregorianCalendar.getMonth();
        int minute = gregorianCalendar.getMinute();
        int hour = gregorianCalendar.getHour();
        
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.YEAR, year);
        calendar.set(Calendar.DAY_OF_MONTH, day);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MONTH, month-1);
        return calendar.getTime();
    }
    
}
