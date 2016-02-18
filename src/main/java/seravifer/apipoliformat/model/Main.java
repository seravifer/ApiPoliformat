package seravifer.apipoliformat.model;

import javafx.util.Pair;
import seravifer.apipoliformat.utils.Utils;

import java.io.File;
import java.util.Scanner;
import java.net.CookieHandler;
import java.net.CookieManager;

public class Main {
    public static void main( String[] args ) throws Exception {
        
        Scanner input = new Scanner( System.in );
        
        String url = "https://intranet.upv.es/pls/soalu/est_intranet.NI_Indiv?P_IDIOMA=c&P_MODO=alumno&P_CUA=sakai&P_VISTA=MSE";
        String auth = "https://www.upv.es/exp/aute_intranet";
        String portal = "https://poliformat.upv.es/portal/tool/2cdd60e6-d777-4c10-a9da-45f76bc23d02/tab-dhtml-moresites";
        
        ApiPoliformat http = new ApiPoliformat();               // Inicia conexion

        CookieHandler.setDefault(new CookieManager());          // Almacena las Cookies
        
        // 1. Extrae la petecion de login
        String page = http.getPageContent(url);                 // Muestra el contenido en texto plano del HTML

        //Pair<String, String> loginResult = Utils.customDialogLogin(Main.class.getResource(".." + File.separator + "view" + File.separator + "Login.fxml"), new Pair<String, String>("", ""));

        System.out.print("DNI: ");
        String dni = input.nextLine();
        System.out.println();
        
        System.out.print("Contraseña: ");
        String pass = input.nextLine();
        System.out.println();
        
        String postParams = http.getFormParams(page, dni, pass);// Extrae la petion del texto plano

        // 2. Manda las peticiones de login
        http.sendPost(postParams);

        // 3. Accede a PoliformaT
        String result = http.getPageContent(portal);            // Muestra el contenido en texto plano del HTML
        //System.out.println(result);

        // 4. Busca las asignaturas
        http.getAsignaturas(result);               // Extrae el nombre de las asignaturas
    }
}