import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.TimeUnit;

public class SiteMap extends RecursiveAction {

    private final List<Link> homepageLinks;
    private static Set<String> uniqueUrls = new HashSet<>();
    private static Set<Link> linksFromSite = new HashSet<>();
    private static final int PROCESSORS = Runtime.getRuntime().availableProcessors();
    private static final ForkJoinPool POOL = new ForkJoinPool(PROCESSORS);
    public static final int DEPTH = 5;
    private static final String LINK_TAG = "a";
    private static final String LINK_ATTRIBUTE = "href";
    private static final String INNER_PAGE_ELEMENT = "#";
    private static final int CONNECTION_TIMEOUT = 0;
    private static final Logger LOGGER = LogManager.getRootLogger();

    private SiteMap(List<Link> homepageLinks) {
        this.homepageLinks = homepageLinks;
    }

    @Override
    protected void compute() {
        if (homepageLinks != null && homepageLinks.size() > 1) {
            List<Link> oneLink = new ArrayList<>();
            oneLink.add(homepageLinks.remove(homepageLinks.size() - 1));

            SiteMap subTaskOne = new SiteMap(oneLink);
            subTaskOne.fork();
            SiteMap subTaskTwo = new SiteMap(homepageLinks);
            subTaskTwo.compute();
            subTaskOne.join();
        } else {
            if (homepageLinks != null) {
                LOGGER.info("Worker started.");
                Link link = homepageLinks.get(0);
                getChildLinks(link);
            }
        }
    }

    private static List<Link> getHomepageLinks(String link) {
        uniqueUrls.add(link);
        linksFromSite.add(new Link(link, 0));
        List<Link> homepageLinks = new ArrayList<>();
        try {
            Document document = Jsoup.connect(link).timeout(CONNECTION_TIMEOUT).get();
            Elements elementsOnPage = document.select(LINK_TAG);
            for (Element element : elementsOnPage) {
                String absUrl = element.absUrl(LINK_ATTRIBUTE);
                if (absUrl.contains(link) && uniqueUrls.add(absUrl)) {
                    Link homepageLink = new Link(absUrl, 1);
                    linksFromSite.add(homepageLink);
                    homepageLinks.add(homepageLink);
                }
            }
        } catch (IOException e) {
            String logMessage = String.format("For %s: ", link);
            LOGGER.error(logMessage, e);
        }
        return homepageLinks;
    }

    private void getChildLinks(Link url) {
        String parentLink = url.getLink();
        int parentLinkLevel = url.getLevel();
        if (parentLinkLevel < DEPTH) {
            try {
                Thread.sleep(120);
                Document doc = Jsoup.connect(parentLink).ignoreContentType(true).timeout(CONNECTION_TIMEOUT).get();
                if (doc != null) {
                    Elements elementsOnPageByLink = doc.select(LINK_TAG);
                    for (Element element : elementsOnPageByLink) {
                        String absUrl = element.absUrl(LINK_ATTRIBUTE);
                        if (absUrl.startsWith(Main.url) && !absUrl.endsWith(INNER_PAGE_ELEMENT) && uniqueUrls.add(absUrl)) {
                            Link link = new Link(absUrl, parentLinkLevel + 1);
                            linksFromSite.add(link);
                            boolean hasChildLinks = absUrl.endsWith("/");
                            if (hasChildLinks) {
                                getChildLinks(link);
                            }
                        }
                    }
                }
            } catch (IOException e) {
                String logMessage = String.format("For %s: ", url);
                LOGGER.error(logMessage, e.getMessage());
            } catch (InterruptedException e) {
                String logMessage = String.format("Thread %s is interrupt.", Thread.currentThread().getName());
                LOGGER.error(logMessage, e.getMessage());
                Thread.currentThread().interrupt();
            }
        }
    }

    public static void writeSitemap(String url, String path) {
        long start = System.currentTimeMillis();
        List<Link> homepageLinks = getHomepageLinks(url);
        SiteMap map = new SiteMap(homepageLinks);
        POOL.invoke(map);

        shutdownAndAwaitTermination();
        if (POOL.isShutdown()) {
            long duration = System.currentTimeMillis() - start;
            linksFromSite.stream()
                    .sorted(Comparator.comparing(Link::getLink).thenComparing(Link::getLevel))
                    .forEach(link -> write(link.toString(), path));
            String linksCountMessage = "Links count: " + linksFromSite.size();
            String durationMessage = String.format("Finished: %02d,%03d s.", TimeUnit.MILLISECONDS.toSeconds(duration),
                    duration - TimeUnit.SECONDS.toMillis(TimeUnit.MILLISECONDS.toSeconds(duration)));
            LOGGER.info(linksCountMessage);
            LOGGER.info(durationMessage);
        }
    }

    private static void shutdownAndAwaitTermination() {
        POOL.shutdown();
        try {
            if (!POOL.awaitTermination(15, TimeUnit.SECONDS)) {
                POOL.shutdownNow();
                if (!POOL.awaitTermination(15, TimeUnit.SECONDS))
                    LOGGER.error("ForkJoinPool did not terminate.");
            }
        } catch (InterruptedException ie) {
            POOL.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static void write(String link, String path) {
        try (FileWriter writer = new FileWriter(path, true)) {
            System.out.println(link);
            writer.write(link);
            writer.append("\n");
            writer.flush();
        } catch (IOException ex) {
            LOGGER.error("Writing to file is not possible", ex);
        }
    }
}