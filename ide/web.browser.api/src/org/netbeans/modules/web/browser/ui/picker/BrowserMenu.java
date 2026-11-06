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
package org.netbeans.modules.web.browser.ui.picker;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.netbeans.modules.web.browser.api.*;
import org.netbeans.modules.web.browser.spi.ProjectBrowserProvider;
import org.openide.util.ChangeSupport;
import org.openide.util.ImageUtilities;
import org.openide.util.RequestProcessor;

/** Compact, keyboard-accessible browser picker with asynchronous device detection. */
public class BrowserMenu implements ChangeListener {
    static final boolean GTK = "GTK".equals(UIManager.getLookAndFeel().getID());
    private static final RequestProcessor DETECTOR = new RequestProcessor(BrowserMenu.class);
    private final Collection<WebBrowser> browsers;
    private final ChangeSupport changes = new ChangeSupport(this);
    private WebBrowser selectedBrowser;
    private JPopupMenu popup;
    private final Set<String> available = new HashSet<>();
    private boolean showAll;
    private boolean detecting;

    public BrowserMenu(ProjectBrowserProvider provider) {
        this(provider.getBrowsers(), provider.getActiveBrowser());
    }

    public BrowserMenu(Collection<WebBrowser> browsers, WebBrowser selectedBrowser) {
        this.browsers = new ArrayList<>(browsers);
        this.selectedBrowser = selectedBrowser;
    }

    public void show(JComponent invoker, int x, int y) {
        popup = new JPopupMenu();
        detecting = browsers.stream().anyMatch(b -> b.getBrowserFamily().isMobile()
                && b.getBrowserFamily() != BrowserFamilyId.PHONEGAP);
        rebuild();
        popup.show(invoker, x, y);
        if (detecting) DETECTOR.post(() -> {
            Set<String> detected = new HashSet<>();
            for (WebBrowser browser : browsers) {
                if (browser.getBrowserFamily().isMobile() && browser.getBrowserFamily() != BrowserFamilyId.PHONEGAP) {
                    try {
                        if (browser.isTargetAvailable()) detected.add(browser.getId());
                    } catch (RuntimeException ex) {
                        // A disconnected device or missing SDK is not an available target.
                    }
                }
            }
            SwingUtilities.invokeLater(() -> {
                available.clear(); available.addAll(detected); detecting = false;
                if (popup.isVisible()) rebuild();
            });
        });
    }

    private void rebuild() {
        popup.removeAll();
        WebBrowser preferred = WebBrowsers.getInstance().getPreferred();
        ArrayList<WebBrowser> sorted = new ArrayList<>(browsers);
        sorted.sort(java.util.Comparator.comparingInt(b ->
                b.getBrowserFamily().isMobile() ? 2 : b.getBrowserFamily() == BrowserFamilyId.FIREFOX ? 0 : 1));
        ButtonGroup group = new ButtonGroup();
        boolean hasMobile = false;
        boolean mobileSection = false;
        for (WebBrowser browser : sorted) {
            if (browser.getBrowserFamily() == BrowserFamilyId.PHONEGAP) continue;
            boolean selected = selectedBrowser != null && selectedBrowser.getId().equals(browser.getId());
            if (browser.getBrowserFamily().isMobile()) {
                hasMobile = true;
                if (!showAll && !selected && !available.contains(browser.getId())) continue;
                if (!mobileSection) { popup.addSeparator(); mobileSection = true; }
            }
            String text = BrowserUISupport.getLongDisplayName(browser);
            if (preferred != null && preferred.getId().equals(browser.getId())) text += "  ·  Default";
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(text,
                    ImageUtilities.image2Icon(browser.getIconImage(true)), selected);
            item.setIconTextGap(10);
            item.setBorder(BorderFactory.createEmptyBorder(7, 10, 7, 14));
            item.addActionListener(e -> { selectedBrowser = browser; changes.fireChange(); });
            group.add(item); popup.add(item);
        }
        if (hasMobile) {
            popup.addSeparator();
            JCheckBoxMenuItem all = new JCheckBoxMenuItem("Show all targets", showAll);
            all.setToolTipText(detecting ? "Checking connected devices…" : "Include disconnected devices and emulators");
            all.addActionListener(e -> {
                showAll = all.isSelected(); rebuild();
                // Menu items normally dismiss their popup; keep the expanded list open.
                SwingUtilities.invokeLater(() -> {
                    if (popup.getInvoker().isShowing()) popup.setVisible(true);
                });
            });
            popup.add(all);
        }
        popup.pack();
    }

    @Override public void stateChanged(ChangeEvent event) { changes.fireChange(); }
    public void addChangeListener(ChangeListener listener) { changes.addChangeListener(listener); }
    public void removeChangeListener(ChangeListener listener) { changes.removeChangeListener(listener); }
    public WebBrowser getSelectedBrowser() { return selectedBrowser; }
}
