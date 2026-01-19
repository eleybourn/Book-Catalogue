package com.eleybourn.bookcatalogue.amazon;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.webkit.WebView;

import com.amazon.device.associates.AssociatesAPI;
import com.amazon.device.associates.LinkService;
import com.amazon.device.associates.OpenSearchPageRequest;
import com.eleybourn.bookcatalogue.utils.Logger;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Wrappers for Amazon API
 * NOTE: The project must include a class, AmazonAppKey in this folder, which contains a
 * single static String, KEY, containing the app key granted by Amazon. For testing purposes
 * this KEY can be junk.
 *
 * @author pjw
 *
 */
public class AmazonUtils {

    public static final String AMAZON_LINK_EXTRAS = "&tag=bookcatalogue-20&linkCode=da5";
    public static final String AMAZON_BOOKS_BASE = "https://www.amazon.com/s?i=stripbooks&k=";

    public static void openLink(Activity context, String author, String series) {
        // Build the URL and args
        String url = AMAZON_BOOKS_BASE;

        author = cleanupSearchString(author);
        series = cleanupSearchString(series);

        String extra = AmazonUtils.buildSearchArgs(author, series);

        if (extra != null && !extra.trim().isEmpty()) {
            url += extra;
        }

        WebView wv = new WebView(context);

        LinkService linkService;

        // Try to setup the API calls; if not possible, just open directly and return
        try {
            // Init Amazon API
            AssociatesAPI.initialize(new AssociatesAPI.Config(AmazonAppKey.KEY, context));
            linkService = AssociatesAPI.getLinkService();
            try {
                linkService.overrideLinkInvocation(wv, url);
            } catch (Exception e2) {
                OpenSearchPageRequest request = new OpenSearchPageRequest("books", author + " " + series);
                linkService.openRetailPage(request);
            }
        } catch (Exception e) {
            Logger.logError(e, "Unable to use Amazon API");
            Intent loadWeb = new Intent(Intent.ACTION_VIEW, Uri.parse(url + AMAZON_LINK_EXTRAS));
            context.startActivity(loadWeb);
        }
    }

    @SuppressWarnings("CharsetObjectCanBeUsed")
    public static String buildSearchArgs(String author, String series) {
        // This code works, but Amazon have a nasty tendency to cancel Associate IDs...
        //String baseUrl = "http://www.amazon.com/gp/search?index=books&tag=philipwarneri-20&tracking_id=philipwarner-20";
        String extra = "";
        try {
            if (author != null && !author.trim().isEmpty()) {
                author = author.replaceAll("\\.,+", " ");
                author = author.replaceAll(" *", "+");
                extra += URLEncoder.encode(author, StandardCharsets.UTF_8.name());
            }
            if (series != null && !series.trim().isEmpty()) {
                series = series.replaceAll("\\.,+", " ");
                series = series.replaceAll(" *", "+");
                extra += " " + URLEncoder.encode(series, StandardCharsets.UTF_8.name());
            }
        } catch (UnsupportedEncodingException e) {
            // This should not happen with UTF-8, which is a standard charset
            Logger.logError(e, "Error encoding URL parameters");
        }
        return extra;

    }

    private static String cleanupSearchString(String search) {
        if (search == null)
            return "";

        StringBuilder out = new StringBuilder(search.length());
        char prev = ' ';
        for (char curr : search.toCharArray()) {
            if (Character.isLetterOrDigit(curr)) {
                out.append(curr);
            } else {
                curr = ' ';
                if (!Character.isWhitespace(prev)) {
                    out.append(curr);
                }
            }
            prev = curr;
        }
        return out.toString();
    }
}
