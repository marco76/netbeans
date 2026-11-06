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
package org.netbeans.modules.java.editor.semantic;

import com.sun.source.tree.*;
import com.sun.source.util.TreePathScanner;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.util.List;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.lang.model.element.*;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;
import org.netbeans.api.java.source.*;
import org.netbeans.spi.editor.SideBarFactory;
import org.netbeans.modules.parsing.api.Source;
import org.openide.filesystems.FileObject;
import org.openide.util.RequestProcessor;

/** Semantic method references in the source classpath, with direct navigation. */
public final class MethodUsagesSideBarFactory implements SideBarFactory {
    private static final RequestProcessor WORKER = new RequestProcessor(MethodUsagesSideBarFactory.class);

    @Override public JComponent createSideBar(JTextComponent target) {
        return new Bar(target);
    }

    static final class Method {
        final int offset;
        final ElementHandle<ExecutableElement> handle;
        final TreePathHandle path;
        final List<Usage> usages = new ArrayList<>();
        Method(int offset, ExecutableElement element, CompilationInfo info) {
            this.offset = offset;
            this.handle = ElementHandle.create(element);
            this.path = TreePathHandle.create(element, info);
        }
    }

    static final class Usage {
        final FileObject file;
        final int offset;
        final long line;
        final String caller;
        final String preview;
        final String reference;
        Usage(FileObject file, int offset, long line, String caller, String preview, String reference) {
            this.file = file;
            this.offset = offset;
            this.line = line;
            this.caller = caller;
            this.preview = preview;
            this.reference = reference;
        }
    }

    static List<Method> collect(JavaSource source, java.util.function.BooleanSupplier cancelled) throws IOException {
        List<Method> methods = new ArrayList<>();
        Set<FileObject> candidates = new LinkedHashSet<>();
        Set<ElementHandle<TypeElement>> owners = new HashSet<>();
        source.runUserActionTask(cc -> {
            cc.toPhase(JavaSource.Phase.RESOLVED);
            candidates.add(cc.getFileObject());
            new TreePathScanner<Void, Void>() {
                @Override public Void visitMethod(MethodTree tree, Void p) {
                    if (cancelled.getAsBoolean()) return null;
                    Element element = cc.getTrees().getElement(getCurrentPath());
                    long offset = cc.getTrees().getSourcePositions().getStartPosition(cc.getCompilationUnit(), tree);
                    if (offset >= 0 && element != null && element.getKind() == ElementKind.METHOD) {
                        methods.add(new Method((int) offset, (ExecutableElement) element, cc));
                        Element owner = element.getEnclosingElement();
                        if (owner instanceof TypeElement && owners.add(ElementHandle.create((TypeElement) owner))) {
                            Set<FileObject> files = cc.getClasspathInfo().getClassIndex().getResources(
                                    ElementHandle.create((TypeElement) owner),
                                    EnumSet.of(ClassIndex.SearchKind.METHOD_REFERENCES),
                                    EnumSet.of(ClassIndex.SearchScope.SOURCE));
                            if (files != null) candidates.addAll(files);
                        }
                    }
                    return super.visitMethod(tree, p);
                }
            }.scan(cc.getCompilationUnit(), null);
        }, true);
        for (FileObject file : candidates) {
            if (cancelled.getAsBoolean()) return List.of();
            JavaSource candidate = JavaSource.forFileObject(file);
            if (candidate == null) continue;
            candidate.runUserActionTask(cc -> {
                cc.toPhase(JavaSource.Phase.RESOLVED);
                Map<Element, Method> resolved = new HashMap<>();
                for (Method method : methods) {
                    Element element = method.handle.resolve(cc);
                    if (element != null) resolved.put(element, method);
                }
                new TreePathScanner<Void, Void>() {
                    private void reference(Tree tree) {
                        Method method = resolved.get(cc.getTrees().getElement(getCurrentPath()));
                        long offset = cc.getTrees().getSourcePositions().getStartPosition(cc.getCompilationUnit(), tree);
                        if (method != null && offset >= 0) {
                            String caller = "Initializer";
                            for (com.sun.source.util.TreePath path = getCurrentPath(); path != null; path = path.getParentPath()) {
                                if (path.getLeaf() instanceof MethodTree) {
                                    caller = ((MethodTree) path.getLeaf()).getName().toString() + "()";
                                    break;
                                }
                            }
                            String text = cc.getText();
                            int start = text.lastIndexOf('\n', (int) offset) + 1;
                            int end = text.indexOf('\n', (int) offset);
                            String preview = text.substring(start, end < 0 ? text.length() : end).strip();
                            method.usages.add(new Usage(file, (int) offset,
                                    cc.getCompilationUnit().getLineMap().getLineNumber(offset), caller, preview, tree.toString()));
                        }
                    }
                    @Override public Void scan(Tree tree, Void p) {
                        return cancelled.getAsBoolean() ? null : super.scan(tree, p);
                    }
                    @Override public Void visitIdentifier(IdentifierTree tree, Void p) {
                        reference(tree);
                        return super.visitIdentifier(tree, p);
                    }
                    @Override public Void visitMemberSelect(MemberSelectTree tree, Void p) {
                        reference(tree);
                        return super.visitMemberSelect(tree, p);
                    }
                    @Override public Void visitMemberReference(MemberReferenceTree tree, Void p) {
                        reference(tree);
                        return super.visitMemberReference(tree, p);
                    }
                }.scan(cc.getCompilationUnit(), null);
            }, true);
        }
        return methods;
    }

    private static final class Bar extends JComponent implements DocumentListener {
        private final JTextComponent editor;
        private final AtomicInteger generation = new AtomicInteger();
        private final RequestProcessor.Task refresh;
        private List<Method> methods = List.of();
        private final Map<Rectangle, Method> links = new LinkedHashMap<>();
        private boolean attached;
        private String status = "Calculating…";
        private final FocusListener focus = new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) { schedule(); }
        };

        Bar(JTextComponent editor) {
            this.editor = editor;
            refresh = WORKER.create(this::compute);
            setToolTipText("Method usages in the source classpath");
            addMouseMotionListener(new MouseMotionAdapter() {
                @Override public void mouseMoved(MouseEvent e) {
                    setCursor(Cursor.getPredefinedCursor(at(e.getPoint()) != null ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
                }
            });
            addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    Method method = at(e.getPoint());
                    if (SwingUtilities.isLeftMouseButton(e) && method != null) showUsages(method, e.getX(), e.getY());
                }
            });
        }
        @Override public Dimension getPreferredSize() {
            Font font = editor.getFont().deriveFont(Math.max(10f, editor.getFont().getSize2D() - 2));
            int width = getFontMetrics(font).stringWidth("00") + 8;
            for (Method method : methods) {
                width = Math.max(width, getFontMetrics(font).stringWidth(Integer.toString(method.usages.size())) + 8);
            }
            return new Dimension(width, 1);
        }
        @Override public String getToolTipText(MouseEvent event) {
            Method method = at(event.getPoint());
            if (method != null) {
                int count = method.usages.size();
                return "<html>" + count + (count == 1 ? " usage" : " usages")
                        + " in the source classpath<br>Click to browse usages"
                        + "<br>Excludes binaries and reflection</html>";
            }
            return status != null ? status : "Method usages in the source classpath";
        }
        @Override public void addNotify() {
            super.addNotify();
            attached = true;
            editor.getDocument().addDocumentListener(this);
            editor.addFocusListener(focus);
            schedule();
        }
        @Override public void removeNotify() {
            attached = false;
            generation.incrementAndGet();
            refresh.cancel();
            editor.getDocument().removeDocumentListener(this);
            editor.removeFocusListener(focus);
            super.removeNotify();
        }
        private void schedule() {
            generation.incrementAndGet();
            if (!SwingUtilities.isEventDispatchThread()) {
                SwingUtilities.invokeLater(this::schedule);
                return;
            }
            methods = List.of();
            status = "Calculating…";
            links.clear();
            repaint();
            if (attached) refresh.schedule(700);
        }
        private void compute() {
            int version = generation.get();
            if (SourceUtils.isScanInProgress()) {
                SwingUtilities.invokeLater(() -> {
                    if (attached && version == generation.get()) { status = "Indexing…"; repaint(); }
                });
                if (version == generation.get()) refresh.schedule(1000);
                return;
            }
            Source parsed = Source.create(editor.getDocument());
            FileObject file = parsed != null ? parsed.getFileObject() : null;
            JavaSource source = file != null ? JavaSource.forFileObject(file) : null;
            if (source == null) return;
            try {
                List<Method> result = collect(source, () -> version != generation.get());
                SwingUtilities.invokeLater(() -> {
                    if (attached && version == generation.get()) {
                        if (SourceUtils.isScanInProgress()) {
                            status = "Indexing…";
                            repaint();
                            refresh.schedule(1000);
                            return;
                        }
                        methods = result;
                        revalidate();
                        status = null;
                        repaint();
                    }
                });
            } catch (IOException ex) {
                SwingUtilities.invokeLater(() -> {
                    if (attached && version == generation.get()) { status = "Unavailable"; repaint(); }
                });
                java.util.logging.Logger.getLogger(MethodUsagesSideBarFactory.class.getName())
                        .log(java.util.logging.Level.FINE, "Cannot compute method usages", ex);
            }
        }
        @Override public void insertUpdate(DocumentEvent e) { schedule(); }
        @Override public void removeUpdate(DocumentEvent e) { schedule(); }
        @Override public void changedUpdate(DocumentEvent e) { }
        private Method at(Point p) {
            for (Map.Entry<Rectangle, Method> link : links.entrySet()) {
                if (link.getKey().contains(p)) return link.getValue();
            }
            return null;
        }
        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setColor(editor.getBackground());
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.setFont(editor.getFont().deriveFont(Math.max(10f, editor.getFont().getSize2D() - 2)));
                Color color = UIManager.getColor("Component.linkColor");
                g.setColor(color != null ? color : editor.getForeground());
                links.clear();
                if (status != null) {
                    Rectangle visible = editor.getVisibleRect();
                    g.drawString("…", 4, visible.y + g.getFontMetrics().getAscent() + 4);
                }
                for (Method method : methods) {
                    try {
                        java.awt.geom.Rectangle2D bounds = editor.modelToView2D(method.offset);
                        if (bounds == null) continue;
                        int y = (int) bounds.getY();
                        Rectangle area = new Rectangle(0, y, getWidth(), (int) bounds.getHeight());
                        if (!g.getClipBounds().intersects(area)) continue;
                        String label = Integer.toString(method.usages.size());
                        g.drawString(label, 4, y + g.getFontMetrics().getAscent());
                        links.put(area, method);
                    } catch (BadLocationException ex) {
                        // A pending document edit will schedule a fresh result.
                    }
                }
            } finally { g.dispose(); }
        }
        private void showUsages(Method method, int x, int y) {
            Action action = org.openide.awt.Actions.forID("Refactoring",
                    "org.netbeans.modules.refactoring.api.ui.WhereUsedAction");
            if (action instanceof org.openide.util.ContextAwareAction) {
                org.openide.nodes.Node node = new org.openide.nodes.AbstractNode(
                        org.openide.nodes.Children.LEAF, org.openide.util.lookup.Lookups.singleton(method.path));
                action = ((org.openide.util.ContextAwareAction) action).createContextAwareInstance(
                        org.openide.util.lookup.Lookups.singleton(node));
            } else {
                action = null;
            }
            MethodUsagesPopup popup = new MethodUsagesPopup(method.usages, action);
            popup.show(this, x, y);
        }
    }
}
