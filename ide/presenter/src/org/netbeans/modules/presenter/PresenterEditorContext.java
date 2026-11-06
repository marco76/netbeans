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

import java.awt.Component;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

final class PresenterEditorContext {
    static FileObject file(JTextComponent editor) {
        if (editor == null) return null;
        Object source = editor.getDocument().getProperty(Document.StreamDescriptionProperty);
        return source instanceof DataObject object ? object.getPrimaryFile() : source instanceof FileObject file ? file : null;
    }
    static TopComponent window(JTextComponent editor) {
        for (Component component = editor; component != null; component = component.getParent()) {
            if (component instanceof TopComponent tc && WindowManager.getDefault().isEditorTopComponent(tc)) return tc;
        }
        return null;
    }
}
