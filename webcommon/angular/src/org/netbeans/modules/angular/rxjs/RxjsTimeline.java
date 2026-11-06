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
package org.netbeans.modules.angular.rxjs;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;

/** Deterministic teaching example, independent of the application's runtime. */
public final class RxjsTimeline extends JPanel {
    private final JComboBox<String> choice = new JComboBox<>(new String[]{"switchMap", "concatMap", "mergeMap", "exhaustMap"});
    private final Canvas timeline = new Canvas();
    public RxjsTimeline() {
        super(new BorderLayout(8, 8));
        JPanel header = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEADING));
        header.add(new JLabel("Flattening operator:")); header.add(choice);
        add(header, BorderLayout.NORTH);
        add(timeline, BorderLayout.CENTER);
        JLabel caption = new JLabel("<html>Teaching simulation: A at 0, B at 2, C at 7; each inner task takes 4 time units.<br>Each task emits once on completion. Crosses show unsubscription; gray entries show ignored input.<br>This diagram does not record your application's runtime emissions.</html>");
        caption.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        add(caption, BorderLayout.SOUTH);
        choice.addActionListener(e -> timeline.repaint());
        setPreferredSize(new Dimension(650, 300));
        getAccessibleContext().setAccessibleName("RxJS flattening operator teaching timeline");
    }
    public record Task(String name, int start, int end, boolean cancelled, boolean ignored) {}
    public static List<Task> tasks(String operator) {
        int[] arrivals = {0, 2, 7};
        List<Task> result = new ArrayList<>();
        int end = 0;
        for (int i = 0; i < arrivals.length; i++) {
            int start = arrivals[i];
            boolean ignored = operator.equals("exhaustMap") && start < end;
            if (operator.equals("concatMap")) start = Math.max(start, end);
            int finish = start + 4;
            boolean cancelled = operator.equals("switchMap") && i + 1 < arrivals.length && arrivals[i + 1] < finish;
            if (cancelled) finish = arrivals[i + 1];
            result.add(new Task(String.valueOf((char) ('A' + i)), start, ignored ? start : finish, cancelled, ignored));
            if (!ignored) end = finish;
        }
        return List.copyOf(result);
    }
    private final class Canvas extends JPanel {
        Canvas() { setPreferredSize(new Dimension(600, 220)); }
        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color foreground = getForeground();
                Color accent = UIManager.getColor("Component.focusColor");
                if (accent == null) accent = foreground;
                int left = 65, right = Math.max(left + 130, getWidth() - 25);
                double unit = (right - left) / 13.0;
                g.setColor(foreground);
                for (int t = 0; t <= 12; t++) {
                    int x = left + (int) (t * unit);
                    g.drawString(Integer.toString(t), x - 3, 25);
                }
                int row = 65;
                for (Task task : tasks((String) choice.getSelectedItem())) {
                    g.setColor(foreground); g.drawString(task.name(), 15, row + 4);
                    int x = left + (int) (task.start() * unit), end = left + (int) (task.end() * unit);
                    g.setColor(task.ignored() ? foreground : accent);
                    if (task.ignored()) g.drawString("ignored", x, row + 4);
                    else {
                        g.setStroke(new BasicStroke(3)); g.drawLine(x, row, end, row);
                        g.fillOval(x - 4, row - 4, 8, 8);
                        if (task.cancelled()) {
                            g.drawLine(end - 5, row - 5, end + 5, row + 5);
                            g.drawLine(end - 5, row + 5, end + 5, row - 5);
                            g.drawString("cancelled", end + 8, row + 4);
                        } else { g.fillOval(end - 5, row - 5, 10, 10); g.drawString(task.name(), end - 4, row + 23); }
                    }
                    row += 55;
                }
            } finally { g.dispose(); }
        }
    }
}
