package nz.co.electech.tag;
import android.app.*; import android.os.*; import android.bluetooth.*; import android.content.pm.PackageManager; import android.widget.*; import android.view.*; import java.util.*; import java.nio.charset.StandardCharsets; import java.util.concurrent.*;
public class MainActivity extends Activity {
 Spinner devices; TextView status; EditText command; Button print; ArrayList<BluetoothDevice> paired=new ArrayList<>(); ExecutorService worker=Executors.newSingleThreadExecutor();
 volatile BluetoothSocket socket; volatile boolean destroyed; Button disconnect; Spinner mode,delay; TextView log;
 public void onCreate(Bundle b){super.onCreate(b); ScrollView scroll=new ScrollView(this); LinearLayout root=new LinearLayout(this); root.setOrientation(1); root.setPadding(24,64,24,32); scroll.addView(root); setContentView(scroll);
 TextView title=new TextView(this); title.setText("ELECTECH | Printer utility V0.3"); title.setTextSize(26); root.addView(title);
 status=new TextView(this); status.setText("Pair your printer in Android settings first."); root.addView(status);
 devices=new Spinner(this); root.addView(devices); Button refresh=new Button(this); refresh.setText("Refresh paired printers"); root.addView(refresh); refresh.setOnClickListener(v->load());
 command=new EditText(this); command.setGravity(Gravity.TOP); command.setMinLines(6); command.setText("^XA\n^FO20,40^A0N,35,35^FDELECTECH^FS\n^FO20,100^A0N,28,28^FDSAMPLE - NOT A TEST RECORD^FS\n^FO20,160^BY2^BCN,65,Y,N,N^FDET000124^FS\n^XZ"); root.addView(command);
 print=new Button(this); print.setText("Send test label"); root.addView(print); print.setOnClickListener(v->send());
 mode=new Spinner(this);mode.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Secure - reconnect each print","Insecure - reconnect each print","Secure - keep open"}));root.addView(mode);
 delay=new Spinner(this);delay.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"500 ms","1000 ms","2000 ms"}));root.addView(delay);
 log=new TextView(this);root.addView(log);
 disconnect=new Button(this); disconnect.setText("Disconnect printer"); root.addView(disconnect); disconnect.setOnClickListener(v->{closeSocket();status.setText("Disconnected");});
 load(); }
 void load(){if(Build.VERSION.SDK_INT>=31 && checkSelfPermission("android.permission.BLUETOOTH_CONNECT")!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{"android.permission.BLUETOOTH_CONNECT"},1);return;} try{BluetoothAdapter a=getSystemService(BluetoothManager.class).getAdapter(); paired.clear(); ArrayList<String> names=new ArrayList<>(); if(a!=null && a.isEnabled())for(BluetoothDevice d:a.getBondedDevices()){paired.add(d);names.add(d.getName()+" ("+d.getAddress()+")");} devices.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,names));status.setText(names.isEmpty()?"No paired devices. Enable Bluetooth and pair printer.":"Select printer.");}catch(Exception e){status.setText(e.toString());}}
 public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);if(g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED)load();}
 void closeSocket(){BluetoothSocket s=socket;socket=null;if(s!=null)try{s.close();}catch(Exception ignored){}}
 void note(String s){runOnUiThread(()->log.append(new java.text.SimpleDateFormat("HH:mm:ss",Locale.US).format(new Date())+" "+s+"\n"));}
 void send(){int i=devices.getSelectedItemPosition();if(i<0||i>=paired.size()){status.setText("Select a paired printer.");return;}BluetoothDevice d=paired.get(i);String data=command.getText().toString();int m=mode.getSelectedItemPosition();int wait=new int[]{500,1000,2000}[delay.getSelectedItemPosition()];print.setEnabled(false);disconnect.setEnabled(false);status.setText("Connecting...");
 worker.execute(()->{boolean started=false;Handler h=new Handler(Looper.getMainLooper());Runnable timeout=null;try{
 if(m!=2||socket==null||!socket.isConnected()||!socket.getRemoteDevice().equals(d)){closeSocket();UUID uuid=UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");socket=m==1?d.createInsecureRfcommSocketToServiceRecord(uuid):d.createRfcommSocketToServiceRecord(uuid);}
 final BluetoothSocket s=socket;timeout=()->{try{s.close();}catch(Exception ignored){}};h.postDelayed(timeout,15000);
 if(!s.isConnected()){note("Opening socket");s.connect();note("Socket opened");}
 Thread.sleep(wait);byte[] bytes=(data+"\n").getBytes(StandardCharsets.US_ASCII);started=true;s.getOutputStream().write(bytes);s.getOutputStream().flush();note("Sent "+bytes.length+" bytes");Thread.sleep(wait);
 java.io.InputStream in=s.getInputStream();int n=in.available();if(n>0){byte[] buf=new byte[Math.min(n,4096)];int count=in.read(buf);if(count>0)note("RX: "+new String(buf,0,count,StandardCharsets.US_ASCII));}else note("No response available");
 if(m!=2){closeSocket();note("Socket closed");}runOnUiThread(()->status.setText("Data sent — verify physical print."));
 }catch(Exception e){closeSocket();note(e.toString());final String msg=started?"Print uncertain — check label before retrying. ":"Not sent — connection failed. ";runOnUiThread(()->status.setText(msg+e.getMessage()));}
 finally{if(timeout!=null)h.removeCallbacks(timeout);runOnUiThread(()->{print.setEnabled(true);disconnect.setEnabled(true);});}});}
 protected void onDestroy(){destroyed=true;closeSocket();worker.shutdownNow();super.onDestroy();}
}