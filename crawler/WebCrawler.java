package crawler;

import java.io.IOException;

/**
 * @author Georgiy Korneev (kgeorgiy@kgeorgiy.info)
 */
public interface WebCrawler {
    Page crawl(String url, int depth) throws IOException;
}
