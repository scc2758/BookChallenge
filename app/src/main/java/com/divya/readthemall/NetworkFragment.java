package com.divya.readthemall;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.app.Fragment;
import android.app.FragmentManager;
import android.util.Xml;

import com.divya.readthemall.Model.Book;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

/**
 * Created by divya on 2/4/2018.
 */

public class NetworkFragment extends Fragment {

    public static final String TAG="NetworkFragment";
    public static final String URL_KEY = "UrlKey";
    private String url;
    private DownloadTask downloadTask;
    private DownloadCallback callback;
    public static NetworkFragment getInstance(FragmentManager manager, String url)
    {
        NetworkFragment frag = (NetworkFragment) manager.findFragmentByTag(NetworkFragment.TAG);
        if(frag == null)
        {
            frag = new NetworkFragment();
            Bundle args = new Bundle();
            args.putString(URL_KEY, url);
            frag.setArguments(args);
            manager.beginTransaction().add(frag, TAG).commit();
        }
        else
        {
            Bundle args = new Bundle();
            args.putString(URL_KEY, url);
            frag.getArguments().putAll(args);
        }

        return frag;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setRetainInstance(true);
        url = getArguments().getString(URL_KEY);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        callback = (DownloadCallback)context;

    }

    @Override
    public void onDetach() {
        super.onDetach();
        callback = null;
    }

    public void startDownload() {
        url = getArguments().getString(URL_KEY);
        System.out.println("Divya url "+url);
        cancelDownload();
        downloadTask = new DownloadTask();
        downloadTask.execute(url);
    }

    public void cancelDownload() {
        if (downloadTask != null) {
            downloadTask.cancel(true);
            downloadTask = null;
        }
    }

    private class DownloadTask extends AsyncTask<String, Integer, DownloadTask.Result> {

        /**
         * Wrapper class that serves as a union of a result value and an exception. When the
         * download task has completed, either the result value or exception can be a non-null
         * value. This allows you to pass exceptions to the UI thread that were thrown during
         * doInBackground().
         */
        class Result {
            public Book bookObj;
            public String resultVal;
            public Exception exception;
            public Result(Book obj){bookObj = obj;}
            public Result(String resultValue) {
                resultVal = resultValue;
            }
            public Result(Exception exception) {
                this.exception = exception;
            }
        }

        /**
         * Cancel background network operation if we do not have network connectivity.
         */
        @Override
        protected void onPreExecute() {
            if (callback != null) {
                NetworkInfo networkInfo = callback.getActiveNetworkInfo();
                if (networkInfo == null || !networkInfo.isConnected() ||
                        (networkInfo.getType() != ConnectivityManager.TYPE_WIFI
                                && networkInfo.getType() != ConnectivityManager.TYPE_MOBILE)) {
                    // If no connectivity, cancel task and update Callback with null data.
                    callback.updateFromDownload(null);
                    cancel(true);
                }
            }
        }

        /**
         * Defines work to perform on the background thread.
         */
        @Override
        protected Result doInBackground(String... urls) {
            Result result = null;
            if (!isCancelled() && urls != null && urls.length > 0) {
                String urlString = urls[0];
                try {
                    URL url = new URL(urlString);
//                    String resultString = downloadUrl(url);
//                    if (resultString != null) {
//                        result = new Result(resultString);
                    Book bookObj = downloadUrl(url);
                    if(bookObj != null)
                    {
                        result = new Result(bookObj);
                    } else {
                        throw new IOException("No response received.");
                    }
                } catch(Exception e) {
                    result = new Result(e);
                }
            }
            return result;
        }

        /**
         * Send DownloadCallback a progress update.
         */
        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);
            if (values.length >= 2) {
                callback.onProgressUpdate(values[0], values[1]);
            }
        }

        /**
         * Updates the DownloadCallback with the result.
         */
        @Override
        protected void onPostExecute(Result result) {
            if (result != null && callback != null) {
                if (result.exception != null) {
                    callback.updateFromDownload(null);
               }
// else if (result.resultVal != null) {
//                    callback.updateFromDownload(result.resultVal);
//                }
                else if(result.bookObj != null)
                {
                    callback.updateFromDownload(result.bookObj);
                }
                callback.finishDownloading();
            }
        }

        /**
         * Override to add special behavior for cancelled AsyncTask.
         */
        @Override
        protected void onCancelled(Result result) {
        }

        /**
         * Given a URL, sets up a connection and gets the HTTP response body from the server.
         * If the network request is successful, it returns the response body in String form. Otherwise,
         * it will throw an IOException.
         */
        private Book downloadUrl(URL url) throws IOException {
            InputStream stream = null;
            HttpsURLConnection connection = null;
            Book result = null;
            try {
                connection = (HttpsURLConnection) url.openConnection();
                // Timeout for reading InputStream arbitrarily set to 3000ms.
                connection.setReadTimeout(3000);
                // Timeout for connection.connect() arbitrarily set to 3000ms.
                connection.setConnectTimeout(3000);
                // For this use case, set HTTP method to GET.
                connection.setRequestMethod("GET");
                // Already true by default but setting just in case; needs to be true since this request
                // is carrying an input (response) body.
                connection.setDoInput(true);
                // Open communications link (network traffic occurs here).
                connection.connect();
                publishProgress(DownloadCallback.Progress.CONNECT_SUCCESS);
                int responseCode = connection.getResponseCode();
                if (responseCode != HttpsURLConnection.HTTP_OK) {
                    throw new IOException("HTTP error code: " + responseCode);
                }
                // Retrieve the response body as an InputStream.
                stream = connection.getInputStream();
                publishProgress(DownloadCallback.Progress.GET_INPUT_STREAM_SUCCESS, 0);
                if (stream != null) {
                    // Converts Stream to String with max length of 500.
                    result = readStream(stream);
                    publishProgress(DownloadCallback.Progress.PROCESS_INPUT_STREAM_SUCCESS, 0);
                }
            } catch (XmlPullParserException e) {
                e.printStackTrace();
            } finally {
                // Close Stream and disconnect HTTPS connection.
                if (stream != null) {
                    stream.close();
                }
                if (connection != null) {
                    connection.disconnect();
                }
            }
            return result;
        }

        /**
         * Converts the contents of an InputStream to a String.
         */
        private Book readStream(InputStream stream) throws IOException, XmlPullParserException {

            String res="";
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(stream, null);
            int eventType = parser.getEventType();
            boolean done = false;
            Book book = null;
            while(eventType != XmlPullParser.END_DOCUMENT && !done)
            {
                //System.out.println("Divya check");

                String name = null;
                switch (eventType)
                {
                    //Book book = null;
                    case XmlPullParser.START_DOCUMENT:
                        book = new Book();
                        book.setRead(false);
                        break;
                    case XmlPullParser.START_TAG:
                        name = parser.getName();
                        if(name.equals("book"))
                        {

                            //res += "Book is ";
                        }
                        else if(name.equals("id"))
                        {
                            res += " id " + parser.nextText();
                        }
                        else if(name.equals("title"))
                        {
                            book.setBookTitle(parser.nextText());
                        }
                        else if(name.equals("image_url"))
                        {
                            book.setImgUrl(parser.nextText());
                        }
                        else if(name.equals("description"))
                        {
                            book.setDescription(parser.nextText());
                        }
                        else if(name.equals("authors"))
                        {
                            String author = getAuthor(parser);
                            book.setAuthor(author);
                        }
                        break;
                    case XmlPullParser.END_TAG:
                        name = parser.getName();
                        if(name.equals("author"))
                        {
                            done = true;
                        }
                        break;
                }
                eventType = parser.next();
            }
            System.out.println("Divya end result is " + book.getBookTitle() + " " + book.getAuthor());

            return book;
//            String result = null;
//            // Read InputStream using the UTF-8 charset.
//            InputStreamReader reader = new InputStreamReader(stream, "UTF-8");
//            // Create temporary buffer to hold Stream data with specified max length.
//            char[] buffer = new char[maxLength];
//            // Populate temporary buffer with Stream data.
//            int numChars = 0;
//            int readSize = 0;
//            while (numChars < maxLength && readSize != -1) {
//                numChars += readSize;
//                int pct = (100 * numChars) / maxLength;
//                publishProgress(DownloadCallback.Progress.PROCESS_INPUT_STREAM_IN_PRGRESS, pct);
//                readSize = reader.read(buffer, numChars, buffer.length - numChars);
//            }
//            if (numChars != -1) {
//                // The stream was not empty.
//                // Create String that is actual length of response body if actual length was less than
//                // max length.
//                numChars = Math.min(numChars, maxLength);
//                result = new String(buffer, 0, numChars);
//            }
//            return result;
        }

        String getAuthor(XmlPullParser parser) throws IOException, XmlPullParserException {
            parser.require(XmlPullParser.START_TAG, null, "authors");

            while(parser.next() != XmlPullParser.END_DOCUMENT)
            {
                if (parser.getEventType() != XmlPullParser.START_TAG) {
                    continue;
                }
                String name = parser.getName();
                if(name.equals("name"))
                {
                    return new String(parser.nextText());
                }
            }
            return "";
        }
    }



}

