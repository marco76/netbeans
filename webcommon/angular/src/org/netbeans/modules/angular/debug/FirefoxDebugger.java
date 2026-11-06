/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.netbeans.modules.angular.debug;

import java.awt.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.prefs.Preferences;
import javax.swing.*;
import org.netbeans.api.project.ProjectUtils;
import org.netbeans.modules.angular.lsp.AngularOptions;
import org.netbeans.modules.angular.project.AngularProject;
import org.netbeans.modules.angular.project.AngularDebugServer;
import org.netbeans.modules.lsp.client.debugger.api.DAPConfiguration;
import org.openide.*;
import org.openide.awt.HtmlBrowser;
import org.openide.filesystems.FileUtil;
import org.openide.modules.Places;
import org.openide.util.*;
import org.openide.windows.IOProvider;

/** Launches Mozilla's Firefox DAP adapter; stdout is reserved for protocol traffic. */
public final class FirefoxDebugger {
    private static final RequestProcessor WORKER=new RequestProcessor(FirefoxDebugger.class.getName(), 3);
    private static final Map<String,FirefoxDebugSession> SESSIONS=new java.util.concurrent.ConcurrentHashMap<>();
    private FirefoxDebugger() {}
    @org.openide.modules.OnStop
    public static final class Cleanup implements Runnable {
        @Override public void run() { for (FirefoxDebugSession session : SESSIONS.values()) session.close(); SESSIONS.clear(); }
    }

    public static void debug(AngularProject project) {
        Preferences prefs=ProjectUtils.getPreferences(project,FirefoxDebugger.class,false);
        Preferences global=NbPreferences.forModule(FirefoxDebugger.class);
        if (!prefs.getBoolean("configured",false) && !configureDetectedDefaults(project,prefs,global)) {
            configure(project,true); return;
        }
        try {
            String node=AngularOptions.getInstance().resolveNodeExecutable(project);
            if (node==null) throw new IllegalArgumentException("Configure Node.js first.");
            var adapter=FirefoxDebugConfiguration.adapter(Path.of(global.get("adapter",discoverAdapter())));
            boolean attach=prefs.getBoolean("attach",false);
            var config=FirefoxDebugConfiguration.configuration(Path.of(prefs.get("webRoot",FileUtil.toFile(project.getProjectDirectory()).getAbsolutePath())),
                    prefs.get("url","http://localhost:4200/"),global.get("firefox",discoverFirefox()),attach,prefs.getInt("port",6000),prefs.get("mappings",""));
            launch(project,node,adapter,config,attach,prefs.getBoolean("startServer",true)&&!attach,prefs.get("serveProject",""),prefs.get("serveConfiguration",""));
        } catch (IllegalArgumentException ex) { error(ex.getMessage()); configure(project,true); }
    }
    public static void stop(AngularProject project) {
        FirefoxDebugSession session=SESSIONS.remove(project.getProjectDirectory().toURI().toString());
        if (session!=null) session.close();
    }
    public static void restart(AngularProject project) { stop(project); debug(project); }

    public static void show(AngularProject project) { configure(project,false); }

    /** Use the normal launch defaults when first-run discovery is unambiguous. */
    private static boolean configureDetectedDefaults(AngularProject project, Preferences prefs, Preferences global) {
        var applications=AngularDebugServer.projects(project);
        if (applications.size()!=1 || AngularOptions.getInstance().resolveNodeExecutable(project)==null) return false;
        String firefox=global.get("firefox",discoverFirefox());
        if (firefox.isBlank() || !Files.isRegularFile(Path.of(firefox))) return false;
        try {
            Path adapter=FirefoxDebugConfiguration.adapter(Path.of(global.get("adapter",discoverAdapter())));
            String application=applications.get(0);
            var target=project.getWorkspace().getProjects().get(application).getTargets().get("serve");
            prefs.put("url","http://localhost:4200/");
            prefs.put("webRoot",FileUtil.toFile(project.getProjectDirectory()).getAbsolutePath());
            prefs.putBoolean("attach",false);
            prefs.putBoolean("startServer",true);
            prefs.put("serveProject",application);
            prefs.put("serveConfiguration",target.getConfigurations().contains("development") ? "development" : "");
            global.put("adapter",adapter.toString());
            global.put("firefox",firefox);
            prefs.putBoolean("configured",true);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }



    private static void configure(AngularProject project,boolean launchAfterSave) {
        Preferences prefs=ProjectUtils.getPreferences(project, FirefoxDebugger.class, false);
        Preferences global=NbPreferences.forModule(FirefoxDebugger.class);
        JTextField url=new JTextField(prefs.get("url", "http://localhost:4200/"), 38);
        JTextField webRoot=new JTextField(prefs.get("webRoot", FileUtil.toFile(project.getProjectDirectory()).getAbsolutePath()), 38);
        JTextField adapter=new JTextField(global.get("adapter", discoverAdapter()), 38);
        JTextField firefox=new JTextField(global.get("firefox", discoverFirefox()), 38);
        JTextArea mappings=new JTextArea(prefs.get("mappings", ""), 4, 38);
        JComboBox<String> mode=new JComboBox<>(new String[]{"Launch a separate Firefox session", "Attach to Firefox debugger"});
        mode.setSelectedIndex(prefs.getBoolean("attach",false) ? 1 : 0);
        JCheckBox startServer=new JCheckBox("Start Angular server and wait until ready",prefs.getBoolean("startServer",true));
        JComboBox<String> serveProject=new JComboBox<>(AngularDebugServer.projects(project).toArray(String[]::new));
        if (!prefs.get("serveProject","").isEmpty()) serveProject.setSelectedItem(prefs.get("serveProject",""));
        JComboBox<String> serveConfiguration=new JComboBox<>();
        Runnable updateConfigurations=() -> {
            serveConfiguration.removeAllItems(); serveConfiguration.addItem("Workspace default");
            var application=project.getWorkspace().getProjects().get((String)serveProject.getSelectedItem());
            if (application!=null && application.getTargets().containsKey("serve")) {
                var configurations=application.getTargets().get("serve").getConfigurations();
                for (String configuration : configurations) serveConfiguration.addItem(configuration);
                String saved=prefs.get("serveConfiguration",configurations.contains("development") ? "development" : "");
                if (configurations.contains(saved)) serveConfiguration.setSelectedItem(saved);
            }
        };
        serveProject.addActionListener(e -> updateConfigurations.run()); updateConfigurations.run();
        JPanel startup=new JPanel(new BorderLayout()); startup.add(startServer,BorderLayout.NORTH); startup.add(serveProject);
        mode.addActionListener(e -> { startServer.setEnabled(mode.getSelectedIndex()==0); serveProject.setEnabled(mode.getSelectedIndex()==0 && startServer.isSelected()); });
        startServer.setEnabled(mode.getSelectedIndex()==0);
        startServer.addActionListener(e -> serveProject.setEnabled(mode.getSelectedIndex()==0 && startServer.isSelected()));
        serveProject.setEnabled(mode.getSelectedIndex()==0 && startServer.isSelected());
        JSpinner port=new JSpinner(new SpinnerNumberModel(prefs.getInt("port",6000),1,65535,1));
        JPanel fields=new JPanel(new GridBagLayout());
        GridBagConstraints c=new GridBagConstraints(); c.insets=new Insets(4,6,4,6); c.fill=GridBagConstraints.HORIZONTAL;
        add(fields,c,0,"Application URL",url); add(fields,c,1,"Local web root",webRoot);
        JPanel adapterRow=new JPanel(new BorderLayout(6,0)); adapterRow.add(adapter);
        JButton choose=new JButton("Browse / Import VSIX…"); adapterRow.add(choose,BorderLayout.EAST);
        add(fields,c,2,"Firefox adapter",adapterRow); add(fields,c,3,"Firefox executable",firefox);
        port.setToolTipText("Attach uses this port. Launch chooses a free port automatically.");
        add(fields,c,4,"Mode",mode); add(fields,c,5,"Attach debugger port",port);
        add(fields,c,6,"Angular application",startup);
        add(fields,c,7,"Serve configuration",serveConfiguration);
        add(fields,c,8,"Path mappings (optional JSON)",new JScrollPane(mappings));
        JTextArea guidance=new JTextArea("Enable script source maps in the development build. Startup waits up to two minutes.\n"
                + "Launch uses a temporary Firefox profile. Attach requires Firefox remote debugging.\n"
                + "Choose Mozilla's Firefox adapter directory, or import its downloaded VSIX.\n"
                + "Path mappings accept ${workspaceFolder}; leave blank for adapter defaults.",4,60);
        guidance.setEditable(false); guidance.setOpaque(false); guidance.setLineWrap(true); guidance.setWrapStyleWord(true);
        add(fields,c,9,"",guidance);
        JButton docs=new JButton("Get Firefox adapter / setup guide");
        docs.addActionListener(e -> {
            try { HtmlBrowser.URLDisplayer.getDefault().showURL(new java.net.URL("https://github.com/firefox-devtools/vscode-firefox-debug")); }
            catch (java.net.MalformedURLException ex) { Exceptions.printStackTrace(ex); }
        });
        add(fields,c,10,"",docs);
        choose.addActionListener(e -> {
            JFileChooser chooser=new JFileChooser(); chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
            if (chooser.showOpenDialog(fields)!=JFileChooser.APPROVE_OPTION) return;
            Path selected=chooser.getSelectedFile().toPath();
            choose.setEnabled(false);
            WORKER.post(() -> {
                try {
                    Path resolved=selected.toString().toLowerCase(Locale.ROOT).endsWith(".vsix")
                            ? FirefoxDebugConfiguration.installVsix(selected, Files.createTempDirectory(Places.getCacheSubdirectory("angular-firefox").toPath(), "adapter-"))
                            : FirefoxDebugConfiguration.adapter(selected);
                    SwingUtilities.invokeLater(() -> { adapter.setText(resolved.toString()); choose.setEnabled(true); });
                } catch (IOException | IllegalArgumentException ex) {
                    SwingUtilities.invokeLater(() -> { error(ex.getMessage()); choose.setEnabled(true); });
                }
            });
        });
        NotifyDescriptor dialog=new NotifyDescriptor(fields,"Debug Angular in Firefox",NotifyDescriptor.OK_CANCEL_OPTION,
                NotifyDescriptor.PLAIN_MESSAGE,null,NotifyDescriptor.OK_OPTION);
        while (DialogDisplayer.getDefault().notify(dialog)==NotifyDescriptor.OK_OPTION) {
            try {
                String node=AngularOptions.getInstance().resolveNodeExecutable(project);
                if (node==null) throw new IllegalArgumentException("Configure Node.js in Angular Settings first.");
                Path script=FirefoxDebugConfiguration.adapter(Path.of(adapter.getText().trim()));
                boolean attach=mode.getSelectedIndex()==1;
                Map<String,Object> config=FirefoxDebugConfiguration.configuration(Path.of(webRoot.getText().trim()),url.getText(),firefox.getText(),attach,(Integer)port.getValue(),mappings.getText());
                String selected=(String)serveProject.getSelectedItem();
                boolean autoStart=startServer.isSelected()&&!attach;
                if (autoStart) AngularDebugServer.arguments(selected,java.net.URI.create(url.getText().trim()));
                prefs.put("url",url.getText().trim()); prefs.put("webRoot",webRoot.getText().trim()); prefs.put("mappings",mappings.getText()); prefs.putInt("port",(Integer)port.getValue());
                global.put("adapter",script.toString()); global.put("firefox",firefox.getText().trim());
                prefs.putBoolean("configured",true); prefs.putBoolean("attach",attach); prefs.putBoolean("startServer",startServer.isSelected());
                prefs.put("serveProject",selected==null ? "" : selected);
                String serveConfig=serveConfiguration.getSelectedIndex()<=0 ? "" : (String)serveConfiguration.getSelectedItem();
                prefs.put("serveConfiguration",serveConfig);
                org.openide.LifecycleManager.getDefault().saveAll();
                if (launchAfterSave) launch(project,node,script,config,attach,autoStart,selected,serveConfig);
                return;
            } catch (IllegalArgumentException ex) { error(ex.getMessage()); }
        }
    }

    private static void launch(AngularProject project,String node,Path adapter,Map<String,Object> config,boolean attach,boolean autoStart,String serveProject,String serveConfiguration) {
        String key=project.getProjectDirectory().toURI().toString();
        FirefoxDebugSession session=new FirefoxDebugSession();
        FirefoxDebugSession old=SESSIONS.putIfAbsent(key,session);
        if (old!=null && !old.isClosed()) { error("This Angular project already has a debug session. Stop it in Output before starting again."); return; }
        if (old!=null) SESSIONS.put(key,session);
        javax.swing.Action stop=new AbstractAction("Stop Angular + Firefox",
                ImageUtilities.loadImageIcon("org/netbeans/modules/angular/debug/debug-stop.svg", false)) {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { session.close(); setEnabled(false); SESSIONS.remove(key,session); }
        };
        var output=IOProvider.getDefault().getIO("Angular Firefox · "+project.getProjectDirectory().getNameExt(),new javax.swing.Action[]{stop}); output.select();
        WORKER.post(() -> {
            try {
                java.net.URI url=java.net.URI.create((String)config.get("url"));
                if (autoStart) {
                    AngularDebugServer.arguments(serveProject,url);
                    if (AngularDebugServer.ready(url)) {
                        Object use="Use running server";
                        NotifyDescriptor choice=new NotifyDescriptor(
                                "A server is already running at "+url+". Use it for this debug session? It will stay running when debugging stops.",
                                "Angular server detected",NotifyDescriptor.OK_CANCEL_OPTION,NotifyDescriptor.QUESTION_MESSAGE,
                                new Object[]{use,NotifyDescriptor.CANCEL_OPTION},use);
                        if (DialogDisplayer.getDefault().notify(choice)!=use) throw new IOException("Debug startup canceled.");
                        output.getOut().println("Using running server at "+url+"; it will remain running after Stop.");
                    } else {
                    output.getOut().println("Starting Angular…");
                    Process server=AngularDebugServer.start(project,serveProject,url,serveConfiguration); session.own(server);
                    pump(server.inputReader(),output.getOut());
                    output.getOut().println("Waiting for Angular at "+url+"…");
                    AngularDebugServer.await(() -> AngularDebugServer.ready(url),server::isAlive,session::isClosed,120000);
                    server.onExit().thenAccept(p -> { if (!session.isClosed()) { output.getErr().println("Angular server exited; stopping this debug session."); session.close(); } });
                    }
                } else {
                    AngularDebugServer.await(() -> AngularDebugServer.ready(url),() -> true,session::isClosed,15000);
                    output.getOut().println("Using an external server; stopping this session will leave that server running.");
                }
                if (session.isClosed()) return;
                FirefoxDebugConfiguration.prepareProfileDirectory(config,
                        Places.getCacheSubdirectory("angular/firefox-profiles").toPath());
                if (!attach) output.getOut().println("Firefox temporary profiles: "+config.get("tmpDir"));
                if (!attach) output.getOut().println("Firefox adapter log: "+((Map<?,?>)config.get("log")).get("fileName"));
                output.getOut().println(attach ? "Attaching Firefox debugger…" : "Opening Firefox…");
                ProcessBuilder builder=new ProcessBuilder(node,adapter.toString()).directory(FileUtil.toFile(project.getProjectDirectory()));
                FirefoxDebugConfiguration.prepareLaunchEnvironment(config,builder.environment());
                FirefoxDebugConfiguration.prepareLaunchPort(config);
                output.getOut().println("Firefox debugger port: "+config.get("port"));
                if (!attach) output.getOut().println("Firefox isolated application data: "+builder.environment().get("MOZ_APP_DATA"));
                Process process=builder.start();
                session.own(process); pump(process.errorReader(),output.getErr());
                InputStream input=new FilterInputStream(process.getInputStream()) {
                    @Override public void close() throws IOException {
                        try { super.close(); } finally { session.close(); }
                    }
                };
                process.onExit().thenAccept(p -> {
                    output.getOut().println("Firefox debug adapter exited ("+p.exitValue()+").");
                    session.close(); SESSIONS.remove(key,session); SwingUtilities.invokeLater(() -> stop.setEnabled(false));
                });
                output.getOut().println("Angular is ready. Connecting Firefox debugger…");
                DAPConfiguration dap=DAPConfiguration.create(input,process.getOutputStream()).addConfiguration(config).setSessionName("Angular · Firefox");
                dap.startAsync(attach).orTimeout(35, java.util.concurrent.TimeUnit.SECONDS)
                        .whenComplete((result, failure) -> {
                            if (failure == null) {
                                if (!session.isClosed()) output.getOut().println("Firefox debugger connected. Ready for breakpoints.");
                            } else {
                                Throwable cause=failure;
                                while (cause.getCause()!=null) cause=cause.getCause();
                                String detail=cause instanceof java.util.concurrent.TimeoutException
                                        ? "Firefox debug adapter did not finish connecting within 35 seconds. Check the adapter and Firefox executable in Firefox Debug Settings."
                                        : cause.toString();
                                output.getErr().println("Firefox debugger connection failed: "+detail);
                                session.close(); SESSIONS.remove(key,session);
                                SwingUtilities.invokeLater(() -> stop.setEnabled(false));
                            }
                        });
            } catch (IOException | IllegalArgumentException | InterruptedException ex) {
                if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
                output.getErr().println("Debug startup stopped: "+ex.getMessage());
                session.close(); SESSIONS.remove(key,session); SwingUtilities.invokeLater(() -> stop.setEnabled(false));
            }
        });
    }
    private static void pump(BufferedReader reader,org.openide.windows.OutputWriter output) {
        Thread thread=new Thread(() -> {
            try (reader) { String line; while ((line=reader.readLine())!=null) output.println(line); }
            catch (IOException ex) { output.println(ex.getMessage()); }
        },"Angular debug output");
        thread.setDaemon(true); thread.start();
    }

    private static void add(JPanel p,GridBagConstraints c,int row,String label,Component field) {
        c.gridy=row; c.gridx=0; c.weightx=0; p.add(new JLabel(label),c);
        c.gridx=1; c.weightx=1; p.add(field,c);
    }
    private static void error(String message) { DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(message,NotifyDescriptor.ERROR_MESSAGE)); }
    private static String discoverFirefox() {
        for (String path : new String[]{"/Applications/Firefox.app/Contents/MacOS/firefox","/usr/bin/firefox","C:\\Program Files\\Mozilla Firefox\\firefox.exe"}) if (new File(path).isFile()) return path;
        return "stable";
    }
    private static String discoverAdapter() {
        Path home=Path.of(System.getProperty("user.home"));
        for (String location : new String[]{".vscode/extensions",".vscode-oss/extensions",".vscode-insiders/extensions"}) {
            Path directory=home.resolve(location);
            if (!Files.isDirectory(directory)) continue;
            try (var extensions=Files.list(directory)) {
                Optional<Path> found=extensions.filter(p -> p.getFileName().toString().startsWith("firefox-devtools.vscode-firefox-debug-"))
                        .sorted(Comparator.reverseOrder()).map(p -> p.resolve("dist/adapter.bundle.js")).filter(Files::isRegularFile).findFirst();
                if (found.isPresent()) return found.get().toString();
            } catch (IOException ex) { /* manual configuration remains available */ }
        }
        return "";
    }
}
