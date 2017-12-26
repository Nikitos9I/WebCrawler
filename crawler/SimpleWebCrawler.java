package crawler;

import java.io.*;
import java.net.*;
import java.util.*;
import java.nio.file.*;
import java.nio.channels.*;
import java.util.regex.*;
import java.util.stream.Collectors;

/**
 * Created by nikitos on 26.12.17.
 */


public class SimpleWebCrawler implements WebCrawler {
    private final Downloader downloader;
    private final Path imagesDirectory;
    private final Map<String, Page> pagesCache = new HashMap<>();
    private final Map<String, Image> imagesCache = new HashMap<>();

    private static String title;
    private static List<String> pageLinks;
    private static List<String> imageLinks;

    private static final Pattern commentPattern = Pattern.compile("<!--(.*?)-->");
    private static final Pattern whitespacePattern = Pattern.compile("(\\p{javaWhitespace}+)");
    private static final Pattern titlePattern = Pattern.compile("<title>(.*?)</title>");
    private static final Pattern linkPattern = Pattern.compile("<a[^>]+href *= *\"(.*?)\"[^>]*>");
    private static final Pattern imagePattern = Pattern.compile("<img[^>]+src *= *\"(.*?)\"[^>]*>");

    public SimpleWebCrawler(Downloader downloader) throws IOException {
        this.downloader = downloader;
        this.imagesDirectory = Paths.get(System.getProperty("user.dir"), "images");
        Files.createDirectories(imagesDirectory);
    }

    @Override
    public Page crawl(String url, int depth) throws IOException {
        if (depth < 0) {
            throw new IllegalArgumentException("Invalid depth: " + depth);
        }

        if (pagesCache.containsKey(url)) {
            return pagesCache.get(url);
        }

        Page result;
        if (depth == 0) {
            result = new Page(url, "");
            pagesCache.put(url, result);
        } else {
            toParse(new URL(url), downloader);
            result = new Page(url, title);
            pagesCache.put(url, result);

            for (String imageLink : imageLinks) {
                result.addImage(processImageLink(imageLink));
            }

            for (String pageLink : pageLinks) {
                result.addLink(crawl(pageLink, depth - 1));
            }
        }

        return result;
    }

    private Image processImageLink(String imageLink) throws IOException {
        if (imagesCache.containsKey(imageLink)) {
            return imagesCache.get(imageLink);
        }

        String name = imageLink.substring(imageLink.lastIndexOf("/") + 1);
        File imagePlace = imagesDirectory.resolve(name).toFile();
        imagePlace.createNewFile();

        try (ReadableByteChannel source = Channels.newChannel(downloader.download(imageLink));
             FileChannel destination = new FileOutputStream(imagePlace).getChannel()) {
            destination.transferFrom(source, 0, Long.MAX_VALUE);
        }

        Image image = new Image(imageLink, imagePlace.getPath());
        imagesCache.put(imageLink, image);

        return image;
    }

    public static void toParse(URL url, Downloader downloader) throws IOException {
        String contents;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(downloader.download(url.toString()), "utf-8"))) {
            contents = reader.lines().collect(Collectors.joining("\n"));
        } catch (IOException e) {
            title = "";
            pageLinks = Collections.emptyList();
            imageLinks = Collections.emptyList();
            return;
        }

        title = unescape(searchTitle(contents));
        contents = HtmlSimplification(contents);

        pageLinks = searchPages(contents, url).stream().map(SimpleWebCrawler::unescape).collect(Collectors.toList());
        imageLinks = searchImages(contents, url);
    }

    private static String HtmlSimplification(String contents) {
        String collapseWhitespaces = whitespacePattern.matcher(contents).replaceAll(" ");
        return commentPattern.matcher(collapseWhitespaces).replaceAll("");
    }

    private static String searchTitle(String contents) {
        Matcher matcher = titlePattern.matcher(contents);
        boolean found = matcher.find();

        return matcher.group(1);
    }

    private static String unescape(String document) {
        return document.replaceAll("&nbsp;", " ").replaceAll("&mdash;", "—")
                .replaceAll("&lt;", "<").replaceAll("&gt;", ">")
                .replaceAll("&amp;", "&");
    }

    private static List<String> searchPages(String contents, URL base) throws MalformedURLException {
        Matcher matcher = linkPattern.matcher(contents);
        List<String> pages = new ArrayList<>();

        while (matcher.find()) {
            String link = matcher.group(1).trim();
            if (link.contains("#")) {
                link = link.substring(0, link.lastIndexOf("#"));
            }
            pages.add(new URL(base, link).toString());
        }

        return pages;
    }

    private static List<String> searchImages(String contents, URL base) throws UnsupportedEncodingException, MalformedURLException {
        Matcher matcher = imagePattern.matcher(contents);
        List<String> result = new ArrayList<>();
        while (matcher.find()) {
            result.add(new URL(base, (matcher.group(1).trim())).toString());
        }

        return result;
    }

}
