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

import org.ros.node.topic.Subscriber;
import ros.android.activity.RosAppActivity;
import ros.android.activity.AppManager;
import android.widget.LinearLayout;
import android.os.Bundle;
import org.ros.node.Node;
import org.ros.exception.RosException;
import android.content.Intent;
import android.text.Spannable;
import android.text.Spannable.Factory;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.app.Activity;
import android.view.MotionEvent;
import android.view.Menu;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.ScrollView;
import android.widget.ListView;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.util.Log;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import java.util.ArrayList;
import org.ros.message.app_manager.AppInstallationState;
import org.ros.message.app_manager.ExchangeApp;
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
import android.app.Dialog;
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
public class ExchangeActivity extends RosAppActivity {
  private TextView robotNameView;
  private TextView exchangeAppNameView;
  private TextView exchangeAppDetailTextView;
  private ListView installedAppListView;
  private ListView availableAppListView;
  private String appSelected;
  private String appSelectedDisplay;
  private ArrayList<ExchangeApp> availableAppsCache;
  private ArrayList<ExchangeApp> installedAppsCache;
  private LinearLayout appExchangeView;
  private LinearLayout installedAppsView;
  private LinearLayout appDetailView;
  private Button installAppButton;
  private Button uninstallAppButton;

  private String[] installed_application_list;
  private String[] installed_application_display;
  private String[] available_application_list;
  private String[] available_application_display;

  private enum State { INSTALLED_APPS, APP_EXCHANGE };
  private State lastState;
  private Dialog dialog;
  private TextView dialog_text;
  private Button button;
  private static final int INSTALLED_ITEM_ID = 0;
  private static final int AVAILABLE_ITEM_ID = 1;
  private static final int INSTALL_DIALOG = 0;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    setDefaultAppName(null);
    setDashboardResource(R.id.exchange_top_bar);
    setMainWindowResource(R.layout.exchange);
    super.onCreate(savedInstanceState);
    setTitle("App Exchange");
    setContentView(R.layout.exchange);
    robotNameView = (TextView) findViewById(R.id.exchange_robot_name_view);
    
    installedAppListView = (ListView)findViewById(R.id.installed_app_list);
    availableAppListView = (ListView)findViewById(R.id.available_app_list);
    appExchangeView = (LinearLayout)findViewById(R.id.app_exchange_view);
    appDetailView = (LinearLayout)findViewById(R.id.app_detail_view);
    installedAppsView = (LinearLayout)findViewById(R.id.installed_apps_view);
    exchangeAppNameView = (TextView)findViewById(R.id.exchange_app_name_view);
    exchangeAppDetailTextView = (TextView)findViewById(R.id.exchange_app_detail_text_view);
    installAppButton = (Button)findViewById(R.id.install_app_button);
    uninstallAppButton = (Button)findViewById(R.id.uninstall_app_button);
    startInstalledApps();
  }

  private static boolean appInList(ArrayList<ExchangeApp> list, String name) {
    for (ExchangeApp a : list) {
      if (a.name == name) {
        return true;
      }
    }
    return false;
  }

  public void installApp(View view) {
    final ExchangeActivity activity = this;
    showDialog(INSTALL_DIALOG);
    appManager.installApp(appSelected, new ServiceResponseListener<InstallApp.Response>() {
      @Override
      public void onSuccess(InstallApp.Response message) {
        if (!message.installed) {
          final String errorMessage = message.message;
          runOnUiThread(new Runnable() {
              @Override
              public void run() {
                new AlertDialog.Builder(activity).setTitle("Error on Installation!").setCancelable(false)
                  .setMessage("ERROR: " + errorMessage)
                  .setNeutralButton("Ok", new DialogInterface.OnClickListener() {
                      public void onClick(DialogInterface dialog, int which) { }})
                  .create().show();
              }});
        }
        
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
              ProgressBar progress_bar = (ProgressBar) dialog.findViewById(R.id.progress_bar);
              progress_bar.setVisibility(View.GONE);
              button.setVisibility(View.VISIBLE);
              
            }}); 
      }
      @Override
      public void onFailure(final RemoteException e) {
        e.printStackTrace();
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
              new AlertDialog.Builder(activity).setTitle("Error on Installation!").setCancelable(false)
                .setMessage("Failed: cannot contact robot: " + e.toString())
                .setNeutralButton("Ok", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) { }})
                .create().show();
              removeDialog(INSTALL_DIALOG);
            }});
      }
    });
  }
 

  public void uninstallApp(View view) {
    final ExchangeActivity activity = this;
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
                new AlertDialog.Builder(activity).setTitle("Error on Uninstallation!").setCancelable(false)
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
              new AlertDialog.Builder(activity).setTitle("Error on Uninstallation").setCancelable(false)
                .setMessage("Failed: cannot contact robot: " + e.toString())
                .setNeutralButton("Ok", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) { }})
                .create().show();
              progress.dismiss();
            }});
      }
    });
  }

  public void startAppExchange(View view) {
    startAppExchange();
  }
  public void startAppExchange() {
    appDetailView.setVisibility(appDetailView.GONE);
    appExchangeView.setVisibility(appExchangeView.VISIBLE);
    installedAppsView.setVisibility(appExchangeView.GONE);
    lastState = State.APP_EXCHANGE;
  }

  public void startInstalledApps(View view) {
    startInstalledApps();
  }
  public void startInstalledApps() {
    appDetailView.setVisibility(appDetailView.GONE);
    appExchangeView.setVisibility(appExchangeView.GONE);
    installedAppsView.setVisibility(appExchangeView.VISIBLE);
    lastState = State.INSTALLED_APPS;
  }

  public void revertToState() {
    appDetailView.setVisibility(appDetailView.GONE);
    switch (lastState) {
    case INSTALLED_APPS:
      startInstalledApps();
      break;
    case APP_EXCHANGE:
      startAppExchange();
      break;
    default:
      Log.e("AppExchangeActivity", "Bad state: " + lastState);
      break;
    }
  }

  public void closeDetailView(View view) {
    appSelected = null;
    appSelectedDisplay = null;
    update(availableAppsCache, installedAppsCache);
  }

  
  public void exitAppExchange(View view) {
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
          final ExchangeApp app = message.app;
          if (app == null) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                  revertToState();
                  appDetailView.setVisibility(appDetailView.GONE);
                  new AlertDialog.Builder(ExchangeActivity.this).setTitle("Error on Details Update!").setCancelable(false)
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
                ImageView iv = (ImageView)ExchangeActivity.this.findViewById(R.id.exchange_icon);
                if( iconBitmap != null ) {
                  iv.setImageBitmap(iconBitmap);
                } else {
                  iv.setImageResource(R.drawable.icon);
                }
                exchangeAppDetailTextView.setText(app.description.toString());
                update(availableAppsCache, installedAppsCache);
              }});
        }
        @Override
        public void onFailure(final RemoteException e) {
          e.printStackTrace();
          runOnUiThread(new Runnable() {
              @Override
              public void run() {
                revertToState();
                appDetailView.setVisibility(appDetailView.GONE);
                new AlertDialog.Builder(ExchangeActivity.this).setTitle("Error on Details Update!").setCancelable(false)
                  .setMessage("Failed: cannot contact robot: " + e.toString())
                  .setNeutralButton("Ok", new DialogInterface.OnClickListener() {
                      public void onClick(DialogInterface dialog, int which) { }})
                  .create().show();
              }});
        }});
  }

  private void update(ArrayList<ExchangeApp> availableApps, ArrayList<ExchangeApp> installedApps) {
    int nInstalledApps = 0;
    int nAvailableApps = 0;

    int i = 0;
    for (ExchangeApp a : installedApps) {
      if (!a.hidden) {
        nInstalledApps++;
      }
    }
    installed_application_list = new String[nInstalledApps];
    installed_application_display = new String[nInstalledApps];
    for (ExchangeApp a : installedApps) {
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
    registerForContextMenu(installedAppListView);
    registerForContextMenu(availableAppListView);
    installedAppListView.setOnItemClickListener(new OnItemClickListener() {
        public void onItemClick(AdapterView adapter, View view, int index, long id) {
          appSelected = installed_application_list_array[index];
          appSelectedDisplay = installed_application_display_array[index];
          Log.i("ExchangeActivity", appSelected);
          ExchangeActivity.this.runOnUiThread(new Runnable() {
              @Override
              public void run() {
                update(availableAppsCache, installedAppsCache);
                installedAppsView.setVisibility(appExchangeView.GONE);
                appExchangeView.setVisibility(appExchangeView.GONE);
                appDetailView.setVisibility(appDetailView.VISIBLE);
                exchangeAppDetailTextView.setText("Loading...");
                ((ImageView)ExchangeActivity.this.findViewById(R.id.exchange_icon)).setImageResource(R.drawable.icon);
              }});
          updateAppDetails();
        }});

    ////
    i = 0;
    for (ExchangeApp a : availableApps) {
      if (!a.hidden) {
        nAvailableApps++;
      }
    }
    available_application_list = new String[nAvailableApps];
    available_application_display = new String[nAvailableApps];
    for (ExchangeApp a : availableApps) {
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
          Log.i("ExchangeActivity", appSelected);
          ExchangeActivity.this.runOnUiThread(new Runnable() {
              @Override
              public void run() {
                update(availableAppsCache, installedAppsCache);
                installedAppsView.setVisibility(appExchangeView.GONE);
                appExchangeView.setVisibility(appExchangeView.GONE);
                appDetailView.setVisibility(appDetailView.VISIBLE);
                exchangeAppDetailTextView.setText("Loading...");
                ((ImageView)ExchangeActivity.this.findViewById(R.id.exchange_icon)).setImageResource(R.drawable.icon);
              }});
          updateAppDetails();
        }});

    availableAppsCache = availableApps;
    installedAppsCache = installedApps;
    
    if (appInList(installedApps, appSelected)) {
      //Is installed
      exchangeAppNameView.setText(appSelectedDisplay + " (Installed)");
      installAppButton.setVisibility(appExchangeView.GONE);
      for (ExchangeApp a : installedApps) {
        if (a.name == appSelected && !a.version.equals(a.latest_version)) {
          exchangeAppNameView.setText(a.display_name + " (Installed, Upgrade Available)");
          installAppButton.setVisibility(appExchangeView.VISIBLE);
        }
      }
      uninstallAppButton.setVisibility(appDetailView.VISIBLE);
    } else if (appInList(availableApps, appSelected)) {
      //Is available
      exchangeAppNameView.setText(appSelectedDisplay + " (Not Installed)");
      installAppButton.setVisibility(appExchangeView.VISIBLE);
      uninstallAppButton.setVisibility(appDetailView.GONE);
    } else {
      appSelected = null; //Bad app!
      appSelectedDisplay = null;
    }

    if (appSelected == null) {
      revertToState();
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
    safeSetStatus("");
    appSelected = null;
  }

  private void runUpdate(boolean remoteUpdate) {
    appManager.listExchangeApps(remoteUpdate, new ServiceResponseListener<GetInstallationState.Response>() {
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
                new AlertDialog.Builder(ExchangeActivity.this).setTitle("Error on List Update!").setCancelable(false)
                  .setMessage("Failed: cannot contact robot: " + e.toString())
                  .setNeutralButton("Ok", new DialogInterface.OnClickListener() {
                      public void onClick(DialogInterface dialog, int which) { }})
                  .create().show();
              }});
        }
      });
  }

  public void updateAppExchange(View view) {
    runUpdate(true);
  }

  @Override
  protected void onNodeCreate(Node node) {
    Log.i("RosAndroid", "ExchangeActivity.onNodeCreate");
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
      appManager.addExchangeListCallback(new MessageListener<AppInstallationState>() {
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
      Log.e("Exchange", "Exception during callback creation");
      e.printStackTrace();
    }
    Subscriber client_sub = node.newSubscriber("install_status","std_msgs/String");
    client_sub.addMessageListener(new MessageListener<org.ros.message.std_msgs.String>() {
            @Override
            public void onNewMessage(final org.ros.message.std_msgs.String data) {
              runOnUiThread(new Runnable() {
              @Override
              public void run() {
                String newline = System.getProperty("line.separator");
                addTextToTextView(newline+data.data);  
              }      
            });
          }   
        }
      );
  }
  @Override
  protected void onNodeDestroy(Node node) {
    Log.i("RosAndroid", "onNodeDestroy");
    super.onNodeDestroy(node);
  }

  public void addTextToTextView(String message) {

    dialog_text.append(message);

    final ScrollView dialog_scroll = (ScrollView) dialog.findViewById(R.id.dialog_scrollview);
    dialog_scroll.post(new Runnable()
    { 
        @Override
        public void run()
        {
            dialog_scroll.fullScroll(View.FOCUS_DOWN);
        }
    });
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

  @Override
  public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
    if (v.getId()==R.id.installed_app_list) {
      AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
      appSelected = installed_application_list[info.position];
      appSelectedDisplay = installed_application_display[info.position];
      menu.setHeaderTitle(appSelectedDisplay);
      String[] menuItems = getResources().getStringArray(R.array.installed_context_menu);
      for (int i = 0; i<menuItems.length; i++) {
        menu.add(INSTALLED_ITEM_ID, i, i, menuItems[i]);
      }
    }
    else if (v.getId()==R.id.available_app_list) {
      AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
      appSelected = available_application_list[info.position];
      appSelectedDisplay = available_application_display[info.position];
      menu.setHeaderTitle(appSelectedDisplay);
      String[] menuItems = getResources().getStringArray(R.array.exchange_context_menu);
      for (int i = 0; i<menuItems.length; i++) {
        menu.add(AVAILABLE_ITEM_ID, i, i, menuItems[i]);
      }
    }
  }

  @Override
  public boolean onContextItemSelected(MenuItem item) {
    AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo)item.getMenuInfo();
    int menuItemIndex = item.getItemId();
    int groupId = item.getGroupId();
    switch (groupId) {
      case INSTALLED_ITEM_ID:
        switch (menuItemIndex) {
          case 0:
            uninstallApp(installedAppListView);
            return true;
          default:
            return false;
        }
      case AVAILABLE_ITEM_ID:
        switch (menuItemIndex) {
          case 0:
            installApp(availableAppListView);
            return true;
          default:
            return false;
        }
      default:
        return false;
    }
  }

  @Override
  protected Dialog onCreateDialog(int id) {
    switch (id) {
      case INSTALL_DIALOG:
        dialog = new Dialog(this);
        dialog.setContentView(R.layout.install_dialog);
        dialog.setTitle("Installation Messages");
        dialog_text = (TextView) dialog.findViewById(R.id.dialog_textview);
        dialog_text.setText("Installing...");
        button = (Button) dialog.findViewById(R.id.ok_button);
        button.setOnClickListener(new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            removeDialog(INSTALL_DIALOG);
          }
        });
        button.setVisibility(View.GONE);
        break;
      default:
        dialog = null;
    }
    return dialog;
  }

}
