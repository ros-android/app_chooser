/*
 * Software License Agreement (BSD License)
 *
 * Copyright (c) 2011, Willow Garage, Inc.
 * All rights reserved.
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *  * Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *  * Redistributions in binary form must reproduce the above
 *    copyright notice, this list of conditions and the following
 *    disclaimer in the documentation and/or other materials provided
 *    with the distribution.
 *  * Neither the name of Willow Garage, Inc. nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *    
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 * ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package org.ros.android.app_chooser;

import ros.android.activity.RosAppActivity;
import android.widget.LinearLayout;
import android.os.Bundle;
import org.ros.node.Node;
import org.ros.exception.RosException;
import android.content.Intent;
import android.app.Activity;
import android.view.Menu;
import android.view.View;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.ListView;
import android.widget.ArrayAdapter;
import android.util.Log;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import java.util.ArrayList;
import org.ros.message.app_manager.App;
import org.ros.node.service.ServiceResponseListener;
import org.ros.service.app_manager.ListApps;
import org.ros.service.app_manager.StartApp;
import org.ros.service.app_manager.StopApp;
import org.ros.message.app_manager.StatusCodes;
import org.ros.message.MessageListener;
import android.app.AlertDialog;
import android.content.DialogInterface;
import org.ros.exception.RemoteException;
import org.ros.message.app_manager.AppList;
import android.widget.Button;
import android.app.ProgressDialog;
import java.util.Map;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URI;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.yaml.snakeyaml.Yaml;
import org.ros.node.parameter.ParameterTree;

/**
 * Show a grid of applications that a given robot is capable of, and launch
 * whichever is chosen.
 */
public class AppStoreActivity extends RosAppActivity {
  private TextView robotNameView;
  private TextView storeAppNameView;
  private TextView storeAppDetailTextView;
  private ListView installedAppListView;
  private ListView availableAppListView;
  private boolean installedAppsView;
  private String appSelected;
  private String appSelectedDisplay;
  private ArrayList<App> availableAppsCache;
  private ArrayList<App> runningAppsCache;
  private LinearLayout appStoreView;
  private LinearLayout appDetailView;
  private Button installAppButton;
  private Button uninstallAppButton;
  private String appStoreUrl;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    setDefaultAppName(null);
    setDashboardResource(R.id.store_top_bar);
    setMainWindowResource(R.layout.store);
    super.onCreate(savedInstanceState);
    setTitle("App Store");
    setContentView(R.layout.store);
    robotNameView = (TextView) findViewById(R.id.store_robot_name_view);
    
    installedAppListView = (ListView)findViewById(R.id.installed_app_list);
    availableAppListView = (ListView)findViewById(R.id.available_app_list);
    appStoreView = (LinearLayout)findViewById(R.id.app_store_view);
    appDetailView = (LinearLayout)findViewById(R.id.app_detail_view);
    storeAppNameView = (TextView)findViewById(R.id.store_app_name_view);
    storeAppDetailTextView = (TextView)findViewById(R.id.store_app_detail_text_view);
    installAppButton = (Button)findViewById(R.id.install_app_button);
    uninstallAppButton = (Button)findViewById(R.id.uninstall_app_button);
  }

  private static boolean appInList(ArrayList<App> list, String name) {
    for (App a : list) {
      if (a.name == name) {
        return true;
      }
    }
    return false;
  }

  public void installApp(View view) {
    final AppStoreActivity activity = this;
    final ProgressDialog progress = ProgressDialog.show(activity,
               "Installing App", "Installing " + appSelectedDisplay + "...", true, false);
    progress.setProgressStyle(ProgressDialog.STYLE_SPINNER);
    appManager.installApp(appSelected, new ServiceResponseListener<StartApp.Response>() {
      @Override
      public void onSuccess(StartApp.Response message) {
        if (!(message.started || message.error_code == StatusCodes.NOT_RUNNING)) {
          final String errorMessage = message.message;
          runOnUiThread(new Runnable() {
              @Override
              public void run() {
                new AlertDialog.Builder(activity).setTitle("Error!").setCancelable(false)
                  .setMessage("ERROR: " + errorMessage)
                  .setNeutralButton("Ok", new DialogInterface.OnClickListener() {
                      public void onClick(DialogInterface dialog, int which) { }})
                  .create().show();
              }});
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
              progress.dismiss();
            }});
      }
      @Override
      public void onFailure(RemoteException e) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
              new AlertDialog.Builder(activity).setTitle("Error!").setCancelable(false)
                .setMessage("Failed: cannot contact robot!")
                .setNeutralButton("Ok", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) { }})
                .create().show();
              progress.dismiss();
            }});
      }
    });
  }

  public void removeApp(View view) {
    final AppStoreActivity activity = this;
    final ProgressDialog progress = ProgressDialog.show(activity,
               "Uninstalling App", "Uninstalling " + appSelectedDisplay + "...", true, false);
    progress.setProgressStyle(ProgressDialog.STYLE_SPINNER);
    appManager.removeApp(appSelected, new ServiceResponseListener<StopApp.Response>() {
      @Override
      public void onSuccess(StopApp.Response message) {
        if (!(message.stopped || message.error_code == StatusCodes.NOT_RUNNING)) {
          final String errorMessage = message.message;
          runOnUiThread(new Runnable() {
              @Override
              public void run() {
                new AlertDialog.Builder(activity).setTitle("Error!").setCancelable(false)
                  .setMessage("ERROR: " + errorMessage)
                  .setNeutralButton("Ok", new DialogInterface.OnClickListener() {
                      public void onClick(DialogInterface dialog, int which) { }})
                  .create().show();
              }});
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
              progress.dismiss();
            }});
      }
      @Override
      public void onFailure(RemoteException e) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
              new AlertDialog.Builder(activity).setTitle("Error!").setCancelable(false)
                .setMessage("Failed: cannot contact robot!")
                .setNeutralButton("Ok", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) { }})
                .create().show();
              progress.dismiss();
            }});
      }
    });
  }

  public void closeDetailView(View view) {
    appSelected = null;
    appSelectedDisplay = null;
    update(availableAppsCache, runningAppsCache);
  }

  
  public void exitAppStore(View view) {
    finish();
  }

  private void update(ArrayList<App> availableApps, ArrayList<App> runningApps) {
    String[] installed_application_list;
    String[] installed_application_display;
    String[] available_application_list;
    String[] available_application_display;

    int i = 0;
    installed_application_list = new String[runningApps.toArray().length];
    installed_application_display = new String[runningApps.toArray().length];
    for (App a : runningApps) {
      installed_application_list[i] =  a.name;
      installed_application_display[i] = a.display_name;
      i = i + 1;
    }

    installedAppListView.setTextFilterEnabled(true);
    

    final String [] installed_application_list_array = installed_application_list;
    final String [] installed_application_display_array = installed_application_display;
    ArrayAdapter ad = new ArrayAdapter(this,android.R.layout.simple_list_item_1, installed_application_display_array);
    installedAppListView.setAdapter(ad);
    installedAppListView.setOnItemClickListener(new OnItemClickListener() {
        public void onItemClick(AdapterView adapter, View view, int index, long id) {
          appSelected = installed_application_list_array[index];
          appSelectedDisplay = installed_application_display_array[index];
          Log.i("AppStoreActivity", appSelected);
          AppStoreActivity.this.runOnUiThread(new Runnable() {
              @Override
              public void run() {
                update(availableAppsCache, runningAppsCache);
                appStoreView.setVisibility(appStoreView.GONE);
                appDetailView.setVisibility(appDetailView.VISIBLE);
              }});
        }});

    ////
    i = 0;
    available_application_list = new String[availableApps.toArray().length];
    available_application_display = new String[availableApps.toArray().length];
    for (App a : availableApps) {
      available_application_list[i] =  a.name;
      available_application_display[i] = a.display_name;
      i = i + 1;
    }

    availableAppListView.setTextFilterEnabled(true);
    

    final String [] available_application_list_array = available_application_list;
    final String [] available_application_display_array = available_application_display;
    ad = new ArrayAdapter(this,android.R.layout.simple_list_item_1, available_application_display_array);
    availableAppListView.setAdapter(ad);
    availableAppListView.setOnItemClickListener(new OnItemClickListener() {
        public void onItemClick(AdapterView adapter, View view, int index, long id) {
          appSelected = available_application_list_array[index];
          appSelectedDisplay = available_application_display_array[index];
          Log.i("AppStoreActivity", appSelected);
          AppStoreActivity.this.runOnUiThread(new Runnable() {
              @Override
              public void run() {
                update(availableAppsCache, runningAppsCache);
                appStoreView.setVisibility(appStoreView.GONE);
                appDetailView.setVisibility(appDetailView.VISIBLE);
              }});
        }});

    availableAppsCache = availableApps;
    runningAppsCache = runningApps;
    
    if (appInList(runningApps, appSelected)) {
      //Is installed
      storeAppNameView.setText(appSelectedDisplay + " (Installed)");
      installAppButton.setVisibility(appStoreView.GONE);
      uninstallAppButton.setVisibility(appDetailView.VISIBLE);
    } else if (appInList(availableApps, appSelected)) {
      //Is available
      storeAppNameView.setText(appSelectedDisplay + " (Not Installed)");
      installAppButton.setVisibility(appStoreView.VISIBLE);
      uninstallAppButton.setVisibility(appDetailView.GONE);
    } else {
      appSelected = null; //Bad app!
      appSelectedDisplay = null;
    }

    if (appSelected == null) {
      appStoreView.setVisibility(appStoreView.VISIBLE);
      appDetailView.setVisibility(appDetailView.GONE);
    } else {
      //TODO run this in another thread
      String url = appStoreUrl + appSelected + ".yaml";
      String page = getPage(url);
      if (page == null) {
        storeAppDetailTextView.setText("Sorry, could not load application info from \"" + url + "\".");
      } else {
        Yaml yaml = new Yaml();
        Map<String, Object> data = (Map<String, Object>)yaml.load(page);
        try {
          storeAppDetailTextView.setText(data.get("description").toString());
        } catch (Exception ex) {
          storeAppDetailTextView.setText("Sorry, this view failed for the file: " + page);
        }
      }
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
    safeSetStatus("");
    appSelected = null;
  }

  @Override
  protected void onNodeCreate(Node node) {
    Log.i("RosAndroid", "AppStoreActivity.onNodeCreate");
    try {
      super.onNodeCreate(node);
    } catch( Exception ex ) {
      safeSetStatus("Failed: " + ex.getMessage());
      node = null;
      return;
    }
    
    runOnUiThread(new Runnable() {
        @Override
        public void run() {
          robotNameView.setText(getCurrentRobot().getRobotName());
        }});
    
    appManager.listStoreApps(new ServiceResponseListener<ListApps.Response>() {
        @Override
        public void onSuccess(ListApps.Response message) {
          availableAppsCache = message.available_apps;
          runningAppsCache = message.running_apps;
          Log.i("RosAndroid", "ListApps.Response: " + availableAppsCache.size() + " apps");
          runOnUiThread(new Runnable() {
              @Override
              public void run() {
                update(availableAppsCache, runningAppsCache);
              }});
        }
        @Override
        public void onFailure(RemoteException e) {
          runOnUiThread(new Runnable() {
              @Override
              public void run() {
                new AlertDialog.Builder(AppStoreActivity.this).setTitle("Error!").setCancelable(false)
                  .setMessage("Failed: cannot contact robot!")
                  .setNeutralButton("Ok", new DialogInterface.OnClickListener() {
                      public void onClick(DialogInterface dialog, int which) { }})
                  .create().show();
              }});
        }
      });
    
    try {
      appManager.addAppStoreListCallback(new MessageListener<AppList>() {
          @Override
          public void onNewMessage(AppList message) {
            availableAppsCache = message.available_apps;
            runningAppsCache = message.running_apps;
            Log.i("RosAndroid", "ListApps.Response: " + availableAppsCache.size() + " apps");
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                  update(availableAppsCache, runningAppsCache);
                }
              });
          }
        });
    } catch (RosException e) {
      e.printStackTrace();
    }

    ParameterTree tree = node.newParameterTree();
    if (tree.has("robot/app_store_directory")) {
      appStoreUrl = tree.getString("robot/app_store_directory");
    }
  }

  private String getPage(String uri) {
    try {
      HttpClient client = new DefaultHttpClient();
      HttpGet request = new HttpGet();
      request.setURI(new URI(uri));
      HttpResponse response = client.execute(request);
      BufferedReader in = new BufferedReader
        (new InputStreamReader(response.getEntity().getContent()));
      StringBuffer sb = new StringBuffer("");
      String line = "";
      String NL = System.getProperty("line.separator");
      while ((line = in.readLine()) != null) {
        sb.append(line + NL);
      }
      in.close();
      String page = sb.toString();
      return page;
    } catch (java.io.IOException ex) {
      Log.e("AppStoreActivity", "IOError: " + uri, ex);
    } catch (java.net.URISyntaxException ex) {
      Log.e("AppStoreActivity", "URI Invalid: " + uri, ex);
    }
    return null;
  }

  @Override
  protected void onNodeDestroy(Node node) {
    Log.i("RosAndroid", "onNodeDestroy");
    super.onNodeDestroy(node);
  }

  private void safeSetStatus(final String statusMessage) {
    final TextView statusView = (TextView) findViewById(R.id.status_view);
    if (statusView != null) {
      statusView.post(new Runnable() {
        @Override
        public void run() {
          statusView.setText(statusMessage);
        }
      });
    }
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.app_chooser_menu, menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
    case R.id.kill:
      android.os.Process.killProcess(android.os.Process.myPid());
      return true;
    default:
      return super.onOptionsItemSelected(item);
    }
  }
}