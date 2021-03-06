package seravifer.apipoliformat.model;

import com.google.gson.reflect.TypeToken;
import seravifer.apipoliformat.utils.GsonUtil;
import seravifer.apipoliformat.utils.Utils;

import java.io.*;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import javax.net.ssl.HttpsURLConnection;

import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Logger;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;


/**
 * Api para PoliformaT de la UPV.
 * Created by Sergi Avila on 12/02/2016.
 */
public class ApiPoliformat {

    public static int attemps = 0;

    private static final Logger logger = (Logger) LoggerFactory.getLogger(ApiPoliformat.class);

    private Map<String, String> subjects;
    private DoubleProperty size;

    public ApiPoliformat(String dni, String pin) throws Exception {
        subjects = new HashMap<>();
        size = new SimpleDoubleProperty(0);

        if(dni.length()==8) attemps++;

        // Inicializa las cookies
        CookieHandler.setDefault(new CookieManager());
        // Manda la petición de login
        sendPost(dni, pin);
        // Busca las asignaturas
        getAsignaturas();
    }

    private void sendPost(String username, String password) throws Exception {
        logger.info("Logeando...");

        String postParams = "&id=c&estilo=500&vista=MSE&cua=sakai&dni=" + username + "&clau=" + password+ "&=Entrar";

        URL link = new URL("https://www.upv.es/exp/aute_intranet");

        HttpsURLConnection conn = (HttpsURLConnection) link.openConnection();
        conn.setDoOutput(true);
        conn.setDoInput(true);

        DataOutputStream post = new DataOutputStream(conn.getOutputStream());
        post.writeBytes(postParams);
        post.flush();
        post.close();

        new BufferedReader(new InputStreamReader(conn.getInputStream()));

        logger.info("Logeo completado");

    }

    /**
     * Almacena en subjects todas las asignaturas en curso junto con su nº de referencia del PoliformaT
     * */
    private void getAsignaturas() throws Exception {
        logger.info("Extrayendo asignaturas...");

        Document doc = Jsoup.connect("https://intranet.upv.es/pls/soalu/sic_asi.Lista_asig").get();

        Elements inputElements = doc.getElementsByClass("upv_enlace");

        for (Element inputElement : inputElements) {

            String oldName = inputElement.ownText();
            String nexName = oldName.substring(0, oldName.length()-2);
            String key     = inputElement.getElementsByTag("span").text().substring(1,6);

            subjects.put(nexName,key);

        }

        for(Map.Entry<String,String> entry : subjects.entrySet()) {
            logger.info( entry.getKey() + " - " + entry.getValue());
        }

        if(subjects.isEmpty()) { logger.warn("DNI o contraseña incorrectas!"); }

        logger.info("Extracción completada!");

    }

    /**
     * Método que recibe el nombre de una asignatura, la descarga en formato Zip desde el PoliformaT y la descomprime en la carpeta donde se ejecuta el programa.
     * @param n Nombre de la asignatura. PRECONDICION: Que esté como key en subjects.
     * */
    public void download(String n) throws IOException {
        System.out.println("Descargando asignatura...");

        String key  = subjects.get(n); // ValueKey - Referencia de la asignatura
        String path = System.getProperty("user.dir") + File.separator;

        // Descargar zip
        URL url = new URL("https://poliformat.upv.es/sakai-content-tool/zipContent.zpc?collectionId=/group/GRA_" + key + "_" + Utils.getCurso() + "/&siteId=GRA_"+ key + "_" + Utils.getCurso());
        logger.debug(url.toString());

        InputStream in       = url.openStream();
        FileOutputStream fos = new FileOutputStream(new File(n + ".zip"));

        Platform.runLater(() -> size.set(0.0));

        int length;
        int downloadedSize = 0;
        byte[] buffer = new byte[2048];
        while ( (length = in.read(buffer)) > -1 ) {
            fos.write(buffer, 0, length);
            downloadedSize += length;
            final int tmp = downloadedSize;
            Platform.runLater(() -> size.set(tmp/(1024.0 * 1024.0 )));
        }
        final int tmp = downloadedSize;
        Platform.runLater(() -> size.set((tmp/(1024*1024.0))));
        fos.close();
        in.close();
        System.out.println("El zip descargado pesa " + Utils.round(Files.size(Paths.get(path + n + ".zip")) / (1024 * 1024.0), 2) + " MB");

        System.out.println("Extrayendo asignatura...");

        // Extrae los archivos del zip
        logger.info("Comenzando la extracción. El zip pesa {} MB", Utils.round(Files.size(Paths.get(path + n + ".zip")) / (1024 * 1024.0), 2));
        String nameFolder = Utils.unZip(path + n + ".zip");
        Map<String, String> nameToAcronym = new HashMap<>();
        nameToAcronym.put(n, nameFolder);
        GsonUtil.appendGson(Paths.get(".namemap").toFile(), nameToAcronym);
        Utils.mkRightNameToURLMaps("https://poliformat.upv.es/access/content/group/GRA_" + key + "_" + Utils.getCurso(), nameFolder + File.separator);

        // Eliminar zip
        File file = new File(path + n + ".zip");
        boolean deleted = file.delete();
        if(!deleted) {
            logger.error("EL ZIP NO HA SIDO BORRADO. SI NO ES BORRARO PUEDE ORIGINAR FALLOS EN FUTURAS DESCARGAS. DEBE BORRARLO MANUALMENTE");
            throw new IOException("El zip de la asignatura no ha sido borrado");
        }

        System.out.println("Descarga completada");
        logger.info("Extracción con éxito");
    }

    /**
     * Método que descarga las diferencias entre la carpeta local de la asignatura y la carpeta del PoliformaT.
     * PRECONDICION: La asignatura ha debido descargarse antes.
     * */
    public void update() {
        Path nameToAcronymPath = Paths.get(".namemap");
        try {
            if(!Files.exists(nameToAcronymPath)) throw new IOException("No existe el mapa de nombre-acronimo de las carpetas de asignatura.");
        } catch (IOException e) {
            logger.warn("No se ha encontrado el mapa traductor nombre-acronimo en: " + nameToAcronymPath, e);
        }
        Map<String, String> map = GsonUtil.leerGson(nameToAcronymPath.toFile(), new TypeToken<Map<String, String>>(){}.getType());
        for(Map.Entry<String, String> entry : map.entrySet()) {
            String name = entry.getKey();
            String acronym = entry.getValue();
            try {
                logger.info("Comienza la actualización de {}", name);
                System.out.println("Actualizando " + name);
                Map<String, String> updateList = Utils.compareLocalFolderTreeAndRemote(Paths.get(acronym), "https://poliformat.upv.es/access/content/group/GRA_" + subjects.get(name) + "_" + Utils.getCurso());
                for(Map.Entry<String, String> s : updateList.entrySet()) {
                    URL url = new URL(s.getKey());
                    InputStream downloadStream = url.openStream();
                    Path filePath = Paths.get(Utils.flattenToAscii(s.getValue()));
                    FileOutputStream file = new FileOutputStream(new File(filePath.toString()));

                    int length;
                    byte[] buffer = new byte[2048];
                    while((length = downloadStream.read(buffer)) > -1) {
                        file.write(buffer, 0, length);
                    }
                    downloadStream.close();
                    file.close();

                    Map<String, String> tmpMap = new HashMap<>();
                    tmpMap.put(filePath.getFileName().toString(), s.getKey());
                    GsonUtil.appendGson(filePath.getParent().resolve(".namemap").toFile(), tmpMap);

                    logger.info("Descargado {} desde {}", s.getValue(), s.getKey());
                }
                logger.info("La actualizacion del {} ha acabado sin problemas", name);
                System.out.println(name + " actualizado");
            } catch (IOException e) {
                logger.warn("No se ha podido actualizar la asignatura: " + name, e);
            }
        }
    }

    public Map<String, String> getSubjects() { return subjects; }

    public DoubleProperty sizeProperty() { return size; }
}