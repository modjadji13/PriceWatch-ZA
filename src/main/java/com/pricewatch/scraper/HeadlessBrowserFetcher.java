package com.pricewatch.scraper;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Renders JavaScript-heavy store pages (Woolworths, Makro, Sixty60, …) in a
 * headless Chromium so their product cards exist in the DOM before we parse them
 * with the same CSS selectors used for server-rendered stores.
 *
 * The browser is expensive to start, so a single instance is launched lazily and
 * shared; each fetch runs in its own short-lived page (cheap and isolated). All
 * of this runs only on the background scrape thread, never on a user request.
 */
@Component
public class HeadlessBrowserFetcher {
    private static final Logger logger = LoggerFactory.getLogger(HeadlessBrowserFetcher.class);
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
        + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36";

    // Off by default: SA's major supermarkets (Woolworths, PnP, Sixty60, Game)
    // detect and block headless Chromium, so rendering them yields nothing.
    // Kept as opt-in infrastructure for JS stores that are not bot-hardened.
    @Value("${scraper.headless.enabled:false}")
    private boolean enabled;

    // Optional path to an already-installed Chromium. Lets us reuse a browser on
    // disk instead of having Playwright download its exact bundled build, which
    // is handy when the download is blocked or a compatible Chromium is present.
    @Value("${scraper.headless.executable-path:}")
    private String executablePath;

    private volatile Playwright playwright;
    private volatile Browser browser;

    /**
     * Loads {@code url}, waits until {@code waitForSelector} appears (the store's
     * product card), and returns the rendered HTML. Returns empty string on any
     * failure so the caller simply treats the store as yielding nothing.
     */
    public String renderHtml(String url, String waitForSelector, int timeoutMs) {
        if (!enabled) {
            return "";
        }

        Browser activeBrowser = ensureBrowser();
        if (activeBrowser == null) {
            return "";
        }

        try (BrowserContext context = activeBrowser.newContext(
            new Browser.NewContextOptions()
                .setUserAgent(USER_AGENT)
                .setExtraHTTPHeaders(Map.of("Accept-Language", "en-ZA,en;q=0.9"))
                .setLocale("en-ZA")
        )) {
            Page page = context.newPage();
            page.setDefaultTimeout(timeoutMs);
            page.navigate(url);
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);

            if (waitForSelector != null && !waitForSelector.isBlank()) {
                try {
                    page.waitForSelector(
                        waitForSelector,
                        new Page.WaitForSelectorOptions()
                            .setState(WaitForSelectorState.ATTACHED)
                            .setTimeout(timeoutMs)
                    );
                } catch (Exception e) {
                    // Selector never appeared; return whatever rendered so the
                    // caller can still attempt a parse rather than hard-failing.
                    logger.debug("Selector '{}' not found on {} within {}ms", waitForSelector, url, timeoutMs);
                }
            }

            return page.content();
        } catch (Exception e) {
            logger.warn("Headless render failed for {}: {}", url, e.getMessage());
            return "";
        }
    }

    private Browser ensureBrowser() {
        Browser current = browser;
        if (current != null) {
            return current;
        }

        synchronized (this) {
            if (browser != null) {
                return browser;
            }

            try {
                playwright = Playwright.create();
                BrowserType.LaunchOptions options = new BrowserType.LaunchOptions()
                    .setHeadless(true)
                    .setArgs(List.of("--no-sandbox", "--disable-blink-features=AutomationControlled"));
                if (executablePath != null && !executablePath.isBlank()) {
                    options.setExecutablePath(java.nio.file.Path.of(executablePath));
                }
                browser = playwright.chromium().launch(options);
                logger.info("Headless Chromium started for JS-rendered stores");
                return browser;
            } catch (Exception e) {
                logger.warn("Headless browser unavailable; JS-rendered stores will be skipped: {}", e.getMessage());
                enabled = false;
                return null;
            }
        }
    }

    @PreDestroy
    public synchronized void shutdown() {
        try {
            if (browser != null) {
                browser.close();
            }
            if (playwright != null) {
                playwright.close();
            }
        } catch (Exception e) {
            logger.debug("Headless browser shutdown error: {}", e.getMessage());
        }
    }
}
