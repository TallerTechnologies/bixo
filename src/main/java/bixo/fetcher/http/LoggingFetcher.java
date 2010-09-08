package bixo.fetcher.http;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;

import org.apache.http.HttpStatus;
import org.apache.log4j.Logger;

import bixo.cascading.Payload;
import bixo.config.FetcherPolicy;
import bixo.config.UserAgent;
import bixo.datum.ContentBytes;
import bixo.datum.FetchedDatum;
import bixo.datum.HttpHeaders;
import bixo.datum.ScoredUrlDatum;
import bixo.datum.UrlStatus;
import bixo.exceptions.BaseFetchException;
import bixo.exceptions.HttpFetchException;
import bixo.exceptions.UrlFetchException;

@SuppressWarnings("serial")
public class LoggingFetcher implements IHttpFetcher {
    private static final Logger LOGGER = Logger.getLogger(LoggingFetcher.class);
    
    public static final String FAKE_CONTENT_LOCATION = "Fake-LoggingFetcher";
    
    // Generic HTML page we send back for every request - only customization is the URL
    private static final String HTML_TEMPLATE =
        "<!DOCTYPE HTML PUBLIC \"-//BBSW//DTD Compact HTML 2.0//EN\">\n" +
        "<html><head><meta http-equiv=\"content-type\" content=\"text/html; charset=utf-8\">\n" +
        "<title>LoggingFetcher</title>\n" +
        "</head><body>URL = %s</body></html>\n";
    
    private int _maxThreads;
    private FetcherPolicy _policy;
    private UserAgent _userAgent;
    
    public LoggingFetcher(int maxThreads) {
        _maxThreads = maxThreads;
        _policy = new FetcherPolicy();
        _userAgent = new UserAgent("agentName", "agentName@domain.com", "http://agentName.domain.com");
    }


    @Override
    public FetchedDatum get(ScoredUrlDatum datum) throws BaseFetchException {
        String url = datum.getUrl();
        Payload payload = datum.getPayload();
        logPayload(url, payload);
        
        // Create a simple HTML page here, where we fill in the URL as
        // the field, and return that as the BytesWritable. we could add
        // more of the datum values to the template if we cared.
        try {
            return makeFetchedDatum(url, String.format(HTML_TEMPLATE, url), payload);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("Should never happen", e);
        } catch (MalformedURLException e) {
            throw new UrlFetchException(url, e.getMessage());
        }
    }

    private FetchedDatum makeFetchedDatum(String url, String htmlContent, Payload payload) throws MalformedURLException, HttpFetchException, UnsupportedEncodingException {
        URL theUrl = new URL(url);
        if (theUrl.getFile().equals("/robots.txt")) {
            throw new HttpFetchException(url, "Never return robots.txt from LoggingFetcher", HttpStatus.SC_NOT_FOUND, null);
        }
        
        byte[] content = htmlContent.getBytes("UTF-8");
        HttpHeaders headers = new HttpHeaders();
        headers.add(IHttpHeaders.CONTENT_LENGTH, "" + content.length);
        headers.add(IHttpHeaders.CONTENT_TYPE, "text/html");
        
        // Set the location to a fixed value, so that when we're processing entries from
        // the URL DB that might have been set using fake content, we know to ignore the
        // refetch time if we're doing a real fetch.
        headers.add(IHttpHeaders.CONTENT_LOCATION, FAKE_CONTENT_LOCATION);
        FetchedDatum result = new FetchedDatum(url, url, System.currentTimeMillis(), headers, new ContentBytes(content), "text/html", 100000);
        result.setPayload(payload);
        return result;
    }


    private void logPayload(String url, Payload payload) {
        StringBuilder msg = new StringBuilder(url);
        msg.append(" ( ");
        for (String key : payload.keySet()) {
            msg.append(key);
            msg.append(':');
            Object value = payload.get(key);
            msg.append(value == null ? "null" : value.toString());
            msg.append(' ');
        }
        msg.append(")");

        LOGGER.info(msg.toString());
    }


    @Override
    public byte[] get(String url) throws BaseFetchException {
        try {
            FetchedDatum result = get(new ScoredUrlDatum(url, "", UrlStatus.UNFETCHED));
            return result.getContentBytes();
        } catch (HttpFetchException e) {
            if (e.getHttpStatus() == HttpStatus.SC_NOT_FOUND) {
                return new byte[0];
            } else {
                throw e;
            }
        }
    }

    @Override
    public FetcherPolicy getFetcherPolicy() {
        return _policy;
    }

    @Override
    public int getMaxThreads() {
        return _maxThreads;
    }


	@Override
	public UserAgent getUserAgent() {
		return _userAgent;
	}


    @Override
    public void abort() {
        // Do nothing
    }

}
