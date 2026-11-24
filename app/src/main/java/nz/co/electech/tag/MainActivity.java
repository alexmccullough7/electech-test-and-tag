package nz.co.electech.tag;
import android.app.*; import android.os.*; import android.bluetooth.*; import android.content.pm.PackageManager; import android.widget.*; import android.view.*; import java.util.*; import java.nio.charset.StandardCharsets; import java.util.concurrent.*;
public class MainActivity extends Activity {
 Spinner devices; TextView status; EditText command; Button print; ArrayList<BluetoothDevice> paired=new ArrayList<>(); ExecutorService worker=Executors.newSingleThreadExecutor();
 volatile BluetoothSocket socket; volatile boolean destroyed; Button disconnect;
 public void onCreate(Bundle b){super.onCreate(b); ScrollView scroll=new ScrollView(this); LinearLayout root=new LinearLayout(this); root.setOrientation(1); root.setPadding(24,64,24,32); scroll.addView(root); setContentView(scroll);
 TextView title=new TextView(this); title.setText("ELECTECH | Printer utility V0.2"); title.setTextSize(26); root.addView(title);
 status=new TextView(this); status.setText("Pair your printer in Android settings first."); root.addView(status);
 devices=new Spinner(this); root.addView(devices); Button refresh=new Button(this); refresh.setText("Refresh paired printers"); root.addView(refresh); refresh.setOnClickListener(v->load());
 command=new EditText(this); command.setGravity(Gravity.TOP); command.setMinLines(6); command.setText("^XA\n^FO20,40^A0N,35,35^FDELECTECH^FS\n^FO20,100^A0N,28,28^FDSAMPLE - NOT A TEST RECORD^FS\n^FO20,160^BY2^BCN,65,Y,N,N^FDET000124^FS\n^XZ"); root.addView(command);
 print=new Button(this); print.setText("Send test label"); root.addView(print); print.setOnClickListener(v->send());
 disconnect=new Button(this); disconnect.setText("Disconnect printer"); root.addView(disconnect); disconnect.setOnClickListener(v->{closeSocket();status.setText("Disconnected");});
 load(); }
 void load(){if(Build.VERSION.SDK_INT>=31 && checkSelfPermission("android.permission.BLUETOOTH_CONNECT")!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{"android.permission.BLUETOOTH_CONNECT"},1);return;} try{BluetoothAdapter a=getSystemService(BluetoothManager.class).getAdapter(); paired.clear(); ArrayList<String> names=new ArrayList<>(); if(a!=null && a.isEnabled())for(BluetoothDevice d:a.getBondedDevices()){paired.add(d);names.add(d.getName()+" ("+d.getAddress()+")");} devices.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,names));status.setText(names.isEmpty()?"No paired devices. Enable Bluetooth and pair printer.":"Select printer.");}catch(Exception e){status.setText(e.toString());}}
 public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);if(g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED)load();}
 void closeSocket(){BluetoothSocket s=socket;socket=null;if(s!=null)try{s.close();}catch(Exception ignored){}}
 BluetoothSocket connect(BluetoothDevice d) throws Exception {
 if(socket!=null && socket.isConnected() && socket.getRemoteDevice().equals(d))return socket;
 closeSocket(); Exception last=null;
 for(int attempt=0;attempt<3&&!destroyed;attempt++){
 BluetoothSocket s=null; Handler h=new Handler(Looper.getMainLooper()); Runnable timeout=null;
 try{UUID uuid=UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
 s=attempt==1?d.createInsecureRfcommSocketToServiceRecord(uuid):d.createRfcommSocketToServiceRecord(uuid);
 socket=s; final BluetoothSocket candidate=s;timeout=()->{try{candidate.close();}catch(Exception ignored){}};
 h.postDelayed(timeout,8000); final int n=attempt+1;runOnUiThread(()->status.setText("Connecting, attempt "+n+" of 3"));s.connect();if(destroyed)throw new java.io.IOException("Activity closed");return s;
 }catch(Exception e){last=e;closeSocket();if(attempt<2)Thread.sleep(1200);}finally{if(timeout!=null)h.removeCallbacks(timeout);}
 }throw new java.io.IOException("Connection failed before sending",last);
 }
 void send(){int i=devices.getSelectedItemPosition();if(i<0||i>=paired.size()){status.setText("Select a paired printer.");return;}BluetoothDevice d=paired.get(i);String data=command.getText().toString();print.setEnabled(false);disconnect.setEnabled(false);status.setText("Preparing print...");
 worker.execute(()->{boolean started=false;Handler h=new Handler(Looper.getMainLooper());Runnable timeout=null;try{BluetoothSocket s=connect(d);timeout=()->{try{s.close();}catch(Exception ignored){}};h.postDelayed(timeout,10000);started=true;s.getOutputStream().write((data+"\n").getBytes(StandardCharsets.US_ASCII));s.getOutputStream().flush();runOnUiThread(()->status.setText("Commands sent; connection kept open. Check physical label."));
 }catch(Exception e){closeSocket();final String msg=started?"Print uncertain — check label before retrying. ":"Not sent — connection failed. ";runOnUiThread(()->status.setText(msg+e.getMessage()));}
 finally{if(timeout!=null)h.removeCallbacks(timeout);runOnUiThread(()->{print.setEnabled(true);disconnect.setEnabled(true);});}});}
 protected void onDestroy(){destroyed=true;closeSocket();worker.shutdownNow();super.onDestroy();}
}