/*
 * Some comments and stub methods Copyright (C) 2008 The Android Open Source Project
 *
 * Copyright (c) 2021 BlackBerry Limited.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.blackberry.bbd.reactnative.ui.webview.bbwebview;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.DragEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.webkit.ValueCallback;
import android.webkit.WebBackForwardList;
import android.webkit.WebHistoryItem;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;

import com.blackberry.bbd.reactnative.ui.webview.R;
import com.blackberry.bbd.reactnative.ui.webview.bbwebview.tasks.http.GDHttpClientProvider;
import com.blackberry.bbd.reactnative.ui.webview.bbwebview.utils.DLPPolicy;
import com.blackberry.bbd.reactnative.ui.webview.bbwebview.utils.Utils;

import java.util.Map;

public class BBWebView extends WebView {

    private static final String TAG = "GDWebView-" + BBWebView.class.getSimpleName();

    private String lastSelectedText = "";

    private BBWebViewClient webViewClient;

    /**
     * Construct a new WebView with a Context object.
     *
     * @param context A Context object used to access application assets.
     */
    public BBWebView(Context context) {
        super(context);

        init();
    }

    /**
     * Construct a new WebView with layout parameters.
     *
     * @param context A Context object used to access application assets.
     * @param attrs   An AttributeSet passed to our parent.
     */
    public BBWebView(Context context, AttributeSet attrs) {
        super(context, attrs);

        init();
    }

    /**
     * Construct a new WebView with layout parameters and a default style.
     *
     * @param context      A Context object used to access application assets.
     * @param attrs        An AttributeSet passed to our parent.
     * @param defStyleAttr
     */
    public BBWebView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        init();
    }

    public BBWebView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        init();
    }

    public void init() {
        Log.i(TAG, "init(), in");

        setOnDragListener(new OnDragListener() {
            @Override
            public boolean onDrag(View v, DragEvent event) {
                Log.i(TAG, "onDrag: requestSelectedText");
                updateTextSelection();
                return false;
            }
        });

        webViewClient = new BBWebViewClient();
        setWebViewClient(webViewClient);

        GDHttpClientProvider.getInstance().initHttpClientsPool();

        BBWebViewClient.init(this, webViewClient);

        Log.i(TAG, "init(), out");
    }

    @Override
    public void destroy() {
        Log.i(TAG, "destroy(), in");

        GDHttpClientProvider.getInstance().disposeHttpClientsPool();

        webViewClient = null;

        super.destroy();

        Log.i(TAG, "destroy(), out");
    }

    @Override
    public void setWebViewClient(@NonNull WebViewClient client) {

        // comment out followings as it need to set even if WebViewClient is BBWebViewClient to continue to receive various notifications and requests.
        //
        // WebViewClient webViewClient = getWebViewClient();
        //
        // if (webViewClient instanceof BBWebViewClient) {
        //     Log.e(TAG, "setWebViewClient(), WebViewClient has already set");
        //     return;
        // }

        boolean isBBWebViewClientPassed = client instanceof BBWebViewClient;

        if (!isBBWebViewClientPassed) {
            Log.e(TAG, "setWebViewClient(), not allowed setting of non WebViewClient");
            return;
        }

        Log.i(TAG, "setWebViewClient(), set WebViewClient - " + client);

        super.setWebViewClient(client);
    }

    @Override
    public boolean onDragEvent(DragEvent event) {

        Log.i(TAG, "onDragEvent: received drag event ");

        switch (event.getAction()) {
            case DragEvent.ACTION_DROP: {
                Log.i(TAG, "onDragEvent: Received ACTION_DROP");

                // There could be 2 cases:
                //
                // 1. Received a drag with drop action which was started from WebView.
                // Then lastSelectedText won't be empty and the condition will be false.
                //
                // 2. Received a drag with drop action which was started from non-GD app (or GD app).
                // Then if DLP is enabled we should prohibit this drop event.
                //
                // Note: The drag-n-drop text from GD app is not handled as it requires SDK support for WebView.
                if (DLPPolicy.isInboundDlpEnabled() && lastSelectedText.isEmpty()) {
                    Log.i(TAG, "onDragEvent: Prevented drag-n-drop text from non-GD app to GDWebView");
                    return true;
                }

                break;
            }
        }

        lastSelectedText = "";

        return super.onDragEvent(event);
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {

        InputConnection connection = super.onCreateInputConnection(outAttrs);

        if (DLPPolicy.isDictationPreventionEnabled()) {
            Log.i(TAG, "onCreateInputConnection: Dictation policy ON");
            outAttrs.privateImeOptions = "nm";
        }

        if (DLPPolicy.isKeyboardRestrictionModeEnabled()) {
            Log.i(TAG, "onCreateInputConnection: Incognito policy ON");
            outAttrs.imeOptions = outAttrs.imeOptions |  EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING;
        }

        return connection;
    }

    @Override
    public void loadUrl(String url, Map<String, String> additionalHttpHeaders) {
        Log.i(TAG, "loadUrl with additionalHttpHeaders - " + Utils.logUrl(url));

        ((BBWebViewClient) getWebViewClient()).getObserver().notifyLoadUrl(url);

        super.loadUrl(url, additionalHttpHeaders);
    }

    @Override
    public void loadUrl(String url) {
        Log.i(TAG, "loadUrl - " + Utils.logUrl(url));

        ((BBWebViewClient) getWebViewClient()).getObserver().notifyLoadUrl(url);

        super.loadUrl(url);
    }

    @Override
    public void goBack() {
        Log.i(TAG, "goBack(), in");

        WebBackForwardList list = copyBackForwardList();

        WebHistoryItem lastItem = list.getCurrentItem();
        int index = list.getCurrentIndex();

        Log.i(TAG, "goBack(), items in the list - " + list.getSize());

        if (lastItem != null && index > 0) {
            WebHistoryItem beforeTheLastItem = list.getItemAtIndex(index - 1);

            Log.i(TAG, "goBack(), the last item {" + lastItem.getTitle() + " : " + lastItem.getUrl() + "}");
            Log.i(TAG, "goBack(), before the last item {" + beforeTheLastItem.getTitle() + " : " + beforeTheLastItem.getUrl() + "}");

            // Skip one item
            if (lastItem.getUrl().equals(beforeTheLastItem.getUrl())) {
                Log.i(TAG, "goBack(), the skip one item ");
                super.goBack();
            }
        }

        super.goBack();
    }

    // AutoFill API.
    // BBD SDK doesn't support AutoFill feature, so SDK overrides and declares final the key part of
    // AutoFill View API which is responsible for AutoFill services on the current widget.
    @Override
    public int getAutofillType() {
        return AUTOFILL_TYPE_NONE;
    }

    private void updateTextSelection() {
        try {
            String script = Utils.getFileContent(R.raw.selection_interceptor, getContext());
            evaluateJavascript(script, new ValueCallback<String>() {
                @Override
                public void onReceiveValue(String value) {
                    // For example, if an user selects 'hello' text in WebView
                    // Then here the received value will be ""hello"".
                    // Remove redundant quotes and save new value.
                    lastSelectedText = value.substring(value.indexOf('"'), value.lastIndexOf('"') - 1);
                }
            });
        } catch (Exception e) {
            Log.i(TAG, "requestSelectedText: exception " + e);
        }
    }

}
