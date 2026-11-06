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
package org.netbeans.modules.angular.ui;

import org.netbeans.api.project.ProjectInformation;
import org.netbeans.modules.angular.model.AngularWorkspace;
import org.netbeans.modules.angular.project.AngularProject;
import org.netbeans.spi.project.ui.LogicalViewProvider;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataFolder;
import org.openide.nodes.FilterNode;
import org.openide.nodes.Node;
import org.openide.nodes.NodeOp;
import org.openide.util.lookup.Lookups;
import org.openide.util.lookup.ProxyLookup;

/** Project tree for an Angular workspace. */
public final class AngularLogicalViewProvider implements LogicalViewProvider {
    private final AngularProject project;

    public AngularLogicalViewProvider(AngularProject project) {
        this.project = project;
    }

    static String compactVersion(String constraint) {
        var match = java.util.regex.Pattern.compile("[~^]?(\\d+\\.\\d+)(?:\\.\\d+)?")
                .matcher(constraint.trim());
        // Complex ranges and tags must not be presented as a specific version.
        return match.matches() ? match.group(1) : constraint;
    }

    private static String escape(String value) {
        try {
            return org.openide.xml.XMLUtil.toElementContent(value);
        } catch (java.io.CharConversionException ex) {
            return "";
        }
    }

    /** Keep specialist tools out of the project's everyday action list. */
    private static final class MenuGroup extends javax.swing.AbstractAction implements org.openide.util.actions.Presenter.Popup {
        private final java.util.List<javax.swing.Action> children;
        MenuGroup(String name, java.util.List<javax.swing.Action> children) {
            super(name);
            this.children = children;
        }
        @Override public void actionPerformed(java.awt.event.ActionEvent event) { }
        @Override public javax.swing.JMenuItem getPopupPresenter() {
            javax.swing.JMenu menu = new javax.swing.JMenu((String) getValue(NAME));
            for (javax.swing.Action child : children) {
                if (child == null) menu.addSeparator();
                else menu.add(new javax.swing.JMenuItem(child));
            }
            return menu;
        }
    }

    @Override
    public Node createLogicalView() {
        Node delegate = DataFolder.findFolder(project.getProjectDirectory()).getNodeDelegate();
        return new FilterNode(delegate, new WorkspaceChildren(delegate),
                new ProxyLookup(Lookups.singleton(project), delegate.getLookup())) {
            @Override
            public javax.swing.Action[] getActions(boolean context) {
                String[][] commands = {
                    {"run", "Run Angular"}, {"debug", "Debug Angular in Firefox"},
                    {"build", "Build"}, {"rebuild", "Rebuild"}, {"ng.build.production", "Build for Production"},
                    {"test", "Test"},
                    {"ng.generate.component", "Generate Component"}, {"ng.generate.service", "Generate Service"},
                    {"ng.generate.directive", "Generate Directive"}, {"ng.generate.pipe", "Generate Pipe"},
                    {"ng.generate.guard", "Generate Guard"}, {"ng.generate.interface", "Generate Interface"}
                };
                java.util.List<javax.swing.Action> actions = new java.util.ArrayList<>();
                java.util.List<javax.swing.Action> generators = new java.util.ArrayList<>();
                java.util.List<javax.swing.Action> tools = new java.util.ArrayList<>();
                var provider = project.getLookup().lookup(org.netbeans.spi.project.ActionProvider.class);
                for (String[] command : commands) {
                    if (java.util.Set.of("build", "test").contains(command[0])) {
                        actions.add(null);
                    }
                    (command[0].startsWith("ng.generate.") ? generators : actions).add(new javax.swing.AbstractAction(command[1], menuIcon(command[0])) {
                        @Override public boolean isEnabled() {
                            return provider.isActionEnabled(command[0], project.getLookup());
                        }
                        @Override public void actionPerformed(java.awt.event.ActionEvent event) {
                            provider.invokeAction(command[0], project.getLookup());
                        }
                    });
                }
                actions.add(null);
                actions.add(new MenuGroup("Generate", generators));
                actions.add(new MenuGroup("Angular Tools", tools));
                tools.add(new javax.swing.AbstractAction("Routes", menuIcon("routes")) {
                    @Override public void actionPerformed(java.awt.event.ActionEvent event) {
                        AngularRoutesExplorer.show(project.getProjectDirectory());
                    }
                });
                tools.add(new javax.swing.AbstractAction("Render Timeline… (Experimental)", menuIcon("timeline")) {
                    @Override public void actionPerformed(java.awt.event.ActionEvent event) {
                        AngularRenderTimeline.show(project.getProjectDirectory());
                    }
                });
                tools.add(new javax.swing.AbstractAction("Component Picker… (Experimental)", menuIcon("picker")) {
                    @Override public void actionPerformed(java.awt.event.ActionEvent event) {
                        AngularComponentPicker.show(project.getProjectDirectory());
                    }
                });
                tools.add(null);
                tools.add(new javax.swing.AbstractAction("Firefox Debug Settings…", menuIcon("settings")) {
                    @Override public void actionPerformed(java.awt.event.ActionEvent event) {
                        org.netbeans.modules.angular.debug.FirefoxDebugger.show(project);
                    }
                });
                tools.add(new javax.swing.AbstractAction("Angular Settings…", menuIcon("settings")) {
                    @Override public void actionPerformed(java.awt.event.ActionEvent event) {
                        org.netbeans.api.options.OptionsDisplayer.getDefault().open("Html5/Angular");
                    }
                });
                actions.add(null);
                actions.add(org.netbeans.spi.project.ui.support.CommonProjectActions.closeProjectAction());
                return actions.toArray(javax.swing.Action[]::new);
            }

            @Override
            public String getDisplayName() {
                return project.getLookup().lookup(ProjectInformation.class).getDisplayName();
            }

            @Override
            public String getHtmlDisplayName() {
                String version = project.getPackageJson().getAngularVersion();
                String name = escape(getDisplayName());
                String color = javax.swing.UIManager.getColor("nb.projecttree.secondaryForeground") != null
                        ? "nb.projecttree.secondaryForeground" : "textInactiveText";
                return version == null ? name : name + "  <font color='!" + color + "'>Angular "
                        + escape(compactVersion(version)) + "</font>";
            }

            @Override
            public String getShortDescription() {
                String version = project.getPackageJson().getAngularVersion();
                return "<html>" + escape(getDisplayName()) + "<br>"
                        + escape(FileUtil.getFileDisplayName(project.getProjectDirectory()))
                        + (version == null ? "" : "<br>Angular dependency: " + escape(version)) + "</html>";
            }

            @Override
            public java.awt.Image getIcon(int type) {
                return org.openide.util.ImageUtilities.loadImage(AngularWorkspace.ICON_RESOURCE);
            }

            @Override
            public java.awt.Image getOpenedIcon(int type) {
                return getIcon(type);
            }
        };
    }

    private static javax.swing.Icon menuIcon(String command) {
        if ("debug".equals(command)) {
            return org.openide.util.ImageUtilities.loadImageIcon(
                    "org/netbeans/modules/angular/debug/firefox-debug.svg", true);
        }
        String name = command.startsWith("ng.generate.") ? "generate" : switch (command) {
            case "run" -> "run";
            case "build", "rebuild", "ng.build.production" -> "build";
            case "test" -> "test";
            default -> command;
        };
        return org.openide.util.ImageUtilities.loadImageIcon(
                "org/netbeans/modules/angular/resources/menu-" + name + ".svg", true);
    }

    /** Decorate every level without replacing file actions, lookups, or live children. */
    private static class FileNode extends FilterNode {
        FileNode(Node original) {
            super(original, new FilterNode.Children(original) {
                @Override protected Node[] createNodes(Node child) {
                    return new Node[]{new FileNode(child)};
                }
            });
        }

        @Override public java.awt.Image getIcon(int type) {
            String icon = fileIcon(getLookup().lookup(FileObject.class));
            return icon == null ? super.getIcon(type)
                    : org.openide.util.ImageUtilities.loadImage(
                            "org/netbeans/modules/angular/resources/file-" + icon + ".svg");
        }

        @Override public java.awt.Image getOpenedIcon(int type) {
            return fileIcon(getLookup().lookup(FileObject.class)) == null
                    ? super.getOpenedIcon(type) : getIcon(type);
        }
    }

    static String fileIcon(FileObject file) {
        if (file == null) return null;
        if (file.isFolder()) return "folder";
        String name = file.getNameExt();
        if (name.endsWith(".ts")) {
            if (name.endsWith(".spec.ts") || name.endsWith(".test.ts")) return "test";
            if (name.endsWith(".config.ts")) return "config";
            if (name.endsWith(".routes.ts") || name.endsWith("-routing.module.ts")) return "routes";
            if (name.endsWith(".component.ts") || file.getParent() != null
                    && file.getParent().getFileObject(file.getName(), "html") != null) return "component";
            return "typescript";
        }
        return switch (file.getExt()) {
            case "html", "htm" -> "html";
            case "scss", "sass" -> "sass";
            default -> null;
        };
    }

    private static final String DEPENDENCIES = "angular-dependencies";
    private static final String TOOLING = "angular-tooling";
    private static final String OUTPUT = "angular-output";

    private static String group(Node node) {
        FileObject file = node.getLookup().lookup(FileObject.class);
        String name = file == null ? node.getName() : file.getNameExt();
        return group(name);
    }

    private static String group(String name) {
        return "node_modules".equals(name) ? DEPENDENCIES
                : "dist".equals(name) ? OUTPUT
                : name.startsWith(".") ? TOOLING : "";
    }

    /** Reuses the folder's live nodes, preserving file actions and filesystem updates. */
    static final class WorkspaceChildren extends org.openide.nodes.Children.Keys<Object> {
        private final Node folder;
        private final java.util.Set<Node> observed = new java.util.HashSet<>();
        private final org.openide.nodes.NodeListener renameListener = new org.openide.nodes.NodeAdapter() {
            @Override public void propertyChange(java.beans.PropertyChangeEvent event) {
                if (Node.PROP_NAME.equals(event.getPropertyName())) refreshKeys();
            }
        };
        private final org.openide.nodes.NodeListener listener = new org.openide.nodes.NodeAdapter() {
            @Override public void childrenAdded(org.openide.nodes.NodeMemberEvent event) { refreshKeys(); }
            @Override public void childrenRemoved(org.openide.nodes.NodeMemberEvent event) { refreshKeys(); }
            @Override public void childrenReordered(org.openide.nodes.NodeReorderEvent event) { refreshKeys(); }
        };

        WorkspaceChildren(Node folder) {
            this.folder = folder;
        }

        @Override protected void addNotify() {
            folder.addNodeListener(listener);
            refreshKeys();
        }

        @Override protected void removeNotify() {
            folder.removeNodeListener(listener);
            for (Node node : observed) node.removeNodeListener(renameListener);
            observed.clear();
            setKeys(java.util.List.of());
        }

        private synchronized void refreshKeys() {
            java.util.List<Object> keys = new java.util.ArrayList<>();
            boolean dependencies = false;
            boolean tooling = false;
            boolean output = false;
            Node[] current = folder.getChildren().getNodes(true);
            java.util.Set<Node> remaining = new java.util.HashSet<>(java.util.Arrays.asList(current));
            for (Node node : observed) {
                if (!remaining.contains(node)) node.removeNodeListener(renameListener);
            }
            for (Node node : current) {
                if (!observed.contains(node)) node.addNodeListener(renameListener);
            }
            observed.clear();
            observed.addAll(remaining);
            for (Node node : current) {
                switch (group(node)) {
                    case DEPENDENCIES -> dependencies = true;
                    case TOOLING -> tooling = true;
                    case OUTPUT -> output = true;
                    default -> keys.add(node);
                }
            }
            if (output) keys.add(OUTPUT);
            if (dependencies) keys.add(DEPENDENCIES);
            if (tooling) keys.add(TOOLING);
            setKeys(keys);
        }

        @Override protected Node[] createNodes(Object key) {
            if (key instanceof Node node) {
                FileObject file = node.getLookup().lookup(FileObject.class);
                if (file != null && file.isFolder()
                        && java.util.Set.of("public", "src").contains(file.getNameExt())) {
                    String name = file.getNameExt();
                    return new Node[]{new FileNode(node) {
                        @Override public String getDisplayName() {
                            return "public".equals(name) ? "Public Assets" : "Sources";
                        }
                        @Override public String getHtmlDisplayName() { return null; }
                        @Override public String getShortDescription() {
                            return "public".equals(name)
                                    ? "public — static website assets served directly by Angular"
                                    : "src — application source code";
                        }
                        @Override public java.awt.Image getIcon(int type) {
                            return org.openide.util.ImageUtilities.loadImage(
                                    "org/netbeans/modules/angular/resources/tree-" + name + ".svg");
                        }
                        @Override public java.awt.Image getOpenedIcon(int type) { return getIcon(type); }
                    }};
                }
                return new Node[]{new FileNode(node)};
            }
            String category = (String) key;
            var children = new FilterNode.Children(folder) {
                @Override protected Node[] createNodes(Node node) {
                    return category.equals(group(node)) ? super.createNodes(node) : new Node[0];
                }
            };
            var node = new org.openide.nodes.AbstractNode(children) {
                @Override public String getHtmlDisplayName() {
                    return "<font color='!textInactiveText'>" + escape(getDisplayName()) + "</font>";
                }
                @Override public java.awt.Image getIcon(int type) {
                    return org.openide.util.ImageUtilities.loadImage(
                            "org/netbeans/modules/angular/resources/tree-" + category + ".svg");
                }
                @Override public java.awt.Image getOpenedIcon(int type) { return getIcon(type); }
            };
            node.setName(category);
            node.setDisplayName(OUTPUT.equals(category) ? "Build Output"
                    : DEPENDENCIES.equals(category) ? "Dependencies" : "Hidden & Tooling");
            node.setShortDescription(OUTPUT.equals(category) ? "Generated Angular build files (dist)"
                    : DEPENDENCIES.equals(category)
                    ? "Installed npm dependencies (node_modules)" : "Hidden files and tooling configuration");
            return new Node[]{node};
        }
    }

    @Override
    public Node findPath(Node root, Object target) {
        if (!(target instanceof FileObject file)) {
            return null;
        }
        String relative = FileUtil.getRelativePath(project.getProjectDirectory(), file);
        if (relative == null) {
            return null;
        }
        if (relative.isEmpty()) {
            return root;
        }
        try {
            String first = relative.split("/", 2)[0];
            String category = group(first);
            Node parent = category.isEmpty() ? root : root.getChildren().findChild(category);
            return parent == null ? null : NodeOp.findPath(parent, relative.split("/"));
        } catch (org.openide.nodes.NodeNotFoundException ex) {
            return null;
        }
    }
}
