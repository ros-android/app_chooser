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
import ros.android.activity.AppManager;
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
import org.ros.message.app_manager.AppInstallationState;
import org.ros.message.app_manager.StoreApp;
import org.ros.service.app_manager.GetAppDetails;
import org.ros.service.app_manager.GetInstallationState;
import org.ros.service.app_manager.InstallApp;
import org.ros.service.app_manager.UninstallApp;
import org.ros.node.service.ServiceResponseListener;
import org.ros.message.MessageListener;
import android.app.AlertDialog;
import android.content.DialogInterface;
import org.ros.exception.RemoteException;
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
import android.graphics.BitmapFactory;
import android.graphics.Bitmap;
import android.widget.ImageView;

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
  private ArrayList<StoreApp> availableAppsCache;
  private ArrayList<StoreApp> installedAppsCache;
  private LinearLayout appStoreView;
  private LinearLayout appDetailView;
  private Button installAppButton;
  private Button uninstallAppButton;

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

  private static boolean appInList(ArrayList<StoreApp> list, String name) {
    for (StoreApp a : list) {
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
    appManager.installApp(appSelected, new ServiceResponseListener<InstallApp.Response>() {
      @Override
      public void onSuccess(InstallApp.Response message) {
        if (!message.installed) {
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
      public void onFailure(final RemoteException e) {
        e.printStackTrace();
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
              new AlertDialog.Builder(activity).setTitle("Error!").setCancelable(false)
                .setMessage("Failed: cannot contact robot: " + e.toString())
                .setNeutralButton("Ok", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) { }})
                .create().show();
              progress.dismiss();
            }});
      }
    });
  }

  public void uninstallApp(View view) {
    final AppStoreActivity activity = this;
    final ProgressDialog progress = ProgressDialog.show(activity,
               "Uninstalling App", "Uninstalling " + appSelectedDisplay + "...", true, false);
    progress.setProgressStyle(ProgressDialog.STYLE_SPINNER);
    appManager.uninstallApp(appSelected, new ServiceResponseListener<UninstallApp.Response>() {
      @Override
      public void onSuccess(UninstallApp.Response message) {
        if (!message.uninstalled) {
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
      public void onFailure(final RemoteException e) {
        e.printStackTrace();
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
              new AlertDialog.Builder(activity).setTitle("Error!").setCancelable(false)
                .setMessage("Failed: cannot contact robot: " + e.toString())
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
    update(availableAppsCache, installedAppsCache);
  }

  
  public void exitAppStore(View view) {
    finish();
  }

  public void updateAppDetails() {
    final AppManager man = appManager;
    if (man == null) {
      return;
    }
    man.getAppDetails(appSelected, new ServiceResponseListener<GetAppDetails.Response>() {
        @Override
        public void onSuccess(GetAppDetails.Response message) {
          final StoreApp app = message.app;
          if (app == null) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                  appStoreView.setVisibility(appStoreView.VISIBLE);
                  appDetailView.setVisibility(appDetailView.GONE);
                  new AlertDialog.Builder(AppStoreActivity.this).setTitle("Error!").setCancelable(false)
                    .setMessage("Failed: cannot contact robot! Null application returned")
                    .setNeutralButton("Ok", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) { }})
                    .create().show();
                }});
            return;
          }
          Bitmap bitmap = null;
          if( app.icon.data.length > 0 && app.icon.format != null &&
              (app.icon.format.equals("jpeg") || app.icon.format.equals("png")) ) {
            bitmap = BitmapFactory.decodeByteArray( app.icon.data, 0, app.icon.data.length );
          }
          final Bitmap iconBitmap = bitmap;
          Log.i("RosAndroid", "GetInstallationState.Response: " + availableAppsCache.size() + " apps");
          runOnUiThread(new Runnable() {
              @Override
              public void run() {
                ImageView iv = (ImageView)AppStoreActivity.this.findViewById(R.id.store_icon);
                if( iconBitmap != null ) {
                  iv.setImageBitmap(iconBitmap);
                } else {
                  iv.setImageResource(R.drawable.icon);
                }
                storeAppDetailTextView.setText(app.description.toString());
                update(availableAppsCache, installedAppsCache);
              }});
        }
        @Override
        public void onFailure(final RemoteException e) {
          e.printStackTrace();
          runOnUiThread(new Runnable() {
              @Override
              public void run() {
                appStoreView.setVisibility(appStoreView.VISIBLE);
                appDetailView.setVisibility(appDetailView.GONE);
                new AlertDialog.Builder(AppStoreActivity.this).setTitle("Error!").setCancelable(false)
                  .setMessage("Failed: cannot contact robot: " + e.toString())
                  .setNeutralButton("Ok", new DialogInterface.OnClickListener() {
                      public void onClick(DialogInterface dialog, int which) { }})
                  .create().show();
              }});
        }});
  }

  private void update(ArrayList<StoreApp> availableApps, ArrayList<StoreApp> installedApps) {
    int nInstalledApps = 0;
    int nAvailableApps = 0;
    String[] installed_application_list;
    String[] installed_application_display;
    String[] available_application_list;
    String[] available_application_display;


    int i = 0;
    for (StoreApp a : installedApps) {
      if (!a.hidden) {
        nInstalledApps++;
      }
    }
    installed_application_list = new String[nInstalledApps];
    installed_application_display = new String[nInstalledApps];
    for (StoreApp a : installedApps) {
      if (!a.hidden) {
        installed_application_list[i] =  a.name;
        if (!a.version.equals(a.latest_version)) {
          installed_application_display[i] = a.display_name + " (Upgradable)";
        } else {
          installed_application_display[i] = a.display_name;
        }
        i = i + 1;
      }
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
                update(availableAppsCache, installedAppsCache);
                appStoreView.setVisibility(appStoreView.GONE);
                appDetailView.setVisibility(appDetailView.VISIBLE);
                storeAppDetailTextView.setText("Loading...");
                ((ImageView)AppStoreActivity.this.findViewById(R.id.store_icon)).setImageResource(R.drawable.icon);
              }});
          updateAppDetails();
        }});

    ////
    i = 0;
    for (StoreApp a : availableApps) {
      if (!a.hidden) {
        nAvailableApps++;
      }
    }
    available_application_list = new String[nAvailableApps];
    available_application_display = new String[nAvailableApps];
    for (StoreApp a : availableApps) {
      if (!a.hidden) {
        available_application_list[i] =  a.name;
        available_application_display[i] = a.display_name;
        i = i + 1;
      }
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
                update(availableAppsCache, installedAppsCache);
                appStoreView.setVisibility(appStoreView.GONE);
                appDetailView.setVisibility(appDetailView.VISIBLE);
                storeAppDetailTextView.setText("Loading...");
                ((ImageView)AppStoreActivity.this.findViewById(R.id.store_icon)).setImageResource(R.drawable.icon);
              }});
          updateAppDetails();
        }});

    availableAppsCache = availableApps;
    installedAppsCache = installedApps;
    
    if (appInList(installedApps, appSelected)) {
      //Is installed
      storeAppNameView.setText(appSelectedDisplay + " (Installed)");
      installAppButton.setVisibility(appStoreView.GONE);
      for (StoreApp a : installedApps) {
        if (a.name == appSelected && !a.version.equals(a.latest_version)) {
          storeAppNameView.setText(a.display_name + " (Installed, Upgrade Available)");
          installAppButton.setVisibility(appStoreView.VISIBLE);
        }
      }
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
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
    safeSetStatus("");
    appSelected = null;
  }

  private void runUpdate(boolean remoteUpdate) {
    appManager.listStoreApps(remoteUpdate, new ServiceResponseListener<GetInstallationState.Response>() {
        @Override
        public void onSuccess(GetInstallationState.Response message) {
          availableAppsCache = message.available_apps;
          installedAppsCache = message.installed_apps;
          Log.i("RosAndroid", "GetInstallationState.Response: " + availableAppsCache.size() + " apps");
          runOnUiThread(new Runnable() {
              @Override
              public void run() {
                update(availableAppsCache, installedAppsCache);
              }});
        }
        @Override
        public void onFailure(final RemoteException e) {
          e.printStackTrace();
          runOnUiThread(new Runnable() {
              @Override
              public void run() {
                new AlertDialog.Builder(AppStoreActivity.this).setTitle("Error!").setCancelable(false)
                  .setMessage("Failed: cannot contact robot: " + e.toString())
                  .setNeutralButton("Ok", new DialogInterface.OnClickListener() {
                      public void onClick(DialogInterface dialog, int which) { }})
                  .create().show();
              }});
        }
      });
  }

  public void updateAppStore(View view) {
    runUpdate(true);
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

    runUpdate(false);
    
    runOnUiThread(new Runnable() {
        @Override
        public void run() {
          robotNameView.setText(getCurrentRobot().getRobotName());
        }});
    
    
    try {
      appManager.addAppStoreListCallback(new MessageListener<AppInstallationState>() {
          @Override
          public void onNewMessage(AppInstallationState message) {
            availableAppsCache = message.available_apps;
            installedAppsCache = message.installed_apps;
            Log.i("RosAndroid", "GetInstallationState.Response: " + availableAppsCache.size() + " apps");
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                  update(availableAppsCache, installedAppsCache);
                }
              });
          }
        });
    } catch (RosException e) {
      Log.e("AppStore", "Exception during callback creation");
      e.printStackTrace();
    }
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