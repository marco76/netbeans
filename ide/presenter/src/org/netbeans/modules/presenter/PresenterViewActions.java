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

import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.util.NbBundle.Messages;

/** One-click view selection from the main View menu. */
@Messages({"CTL_NormalView=Normal View", "CTL_LiveTalkView=Presentation: Live Talk Preset", "CTL_YouTubeView=Presentation: Recording Preset"})
public final class PresenterViewActions {
    private PresenterViewActions() { }

    @SuppressWarnings("serial")
    private abstract static class ViewAction extends AbstractAction {
        private final PresenterPresets.Preset preset;

        ViewAction(String name, PresenterPresets.Preset preset) {
            super(name);
            this.preset = preset;
            PresenterModeAction.addViewListener(event -> updateSelected());
            updateSelected();
        }

        private void updateSelected() {
            putValue(SELECTED_KEY, PresenterModeAction.currentView() == preset);
        }

        @Override
        public void actionPerformed(ActionEvent event) {
            PresenterModeAction.selectView(preset);
        }
    }

    @ActionID(category = "View", id = "org.netbeans.modules.presenter.NormalView")
    @ActionRegistration(displayName = "#CTL_NormalView", lazy = false)
    @SuppressWarnings("serial")
    public static final class NormalView extends ViewAction {
        public NormalView() { super(Bundle.CTL_NormalView(), null); }
    }

    @ActionID(category = "View", id = "org.netbeans.modules.presenter.LiveTalkView")
    @ActionRegistration(displayName = "#CTL_LiveTalkView", lazy = false)
    @SuppressWarnings("serial")
    public static final class LiveTalkView extends ViewAction {
        public LiveTalkView() { super(Bundle.CTL_LiveTalkView(), PresenterPresets.Preset.LIVE_TALK); }
    }

    @ActionID(category = "View", id = "org.netbeans.modules.presenter.YouTubeView")
    @ActionRegistration(displayName = "#CTL_YouTubeView", lazy = false)
    @SuppressWarnings("serial")
    public static final class YouTubeView extends ViewAction {
        public YouTubeView() { super(Bundle.CTL_YouTubeView(), PresenterPresets.Preset.YOUTUBE); }
    }
}
