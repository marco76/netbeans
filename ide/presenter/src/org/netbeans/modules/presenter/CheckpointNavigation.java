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
package org.netbeans.modules.presenter;

import javax.swing.JEditorPane;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;

/** Split editors may return a hidden pane first; focus is not an opening prerequisite. */
final class CheckpointNavigation {
    static JEditorPane choose(JEditorPane[] panes, Document document, JTextComponent focused) {
        if (panes == null) return null;
        for (JEditorPane pane : panes) if (pane == focused && pane.getDocument() == document && pane.isShowing() && pane.getWidth() > 0 && pane.getHeight() > 0) return pane;
        for (JEditorPane pane : panes) if (pane.getDocument() == document && pane.isShowing() && pane.getWidth() > 0 && pane.getHeight() > 0) return pane;
        return null;
    }
    static void position(JTextComponent pane, DemoCheckpoint entry) throws javax.swing.text.BadLocationException {
        int start = Math.min(entry.start(), pane.getDocument().getLength());
        pane.setCaretPosition(start);
        java.awt.geom.Rectangle2D rectangle = pane.modelToView2D(start);
        if (rectangle != null) {
            java.awt.Rectangle visible = pane.getVisibleRect();
            java.awt.Rectangle context = rectangle.getBounds();
            context.y = Math.max(0, context.y - visible.height / 3);
            context.height = Math.max(context.height, visible.height);
            pane.scrollRectToVisible(context);
        }
    }
}
