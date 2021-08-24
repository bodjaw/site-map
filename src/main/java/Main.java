import java.io.File;

public class Main {

    public static String url = "https://secure-headland-59304.herokuapp.com/";
    public static String path = "map" + File.separator + "test.txt";

    public static void main(String[] args) {
        SiteMap.writeSitemap(url, path);
    }
}
