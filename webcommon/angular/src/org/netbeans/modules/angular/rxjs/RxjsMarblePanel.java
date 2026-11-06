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
import java.util.List;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/** Editable marble playground with synchronized diagram, output and test code. */
final class RxjsMarblePanel extends JPanel {
    private final JTextField input = new JTextField("-a-a-b-c-|", 22);
    private final JComboBox<String> operator = new JComboBox<>(RxjsMarbles.OPERATORS.toArray(String[]::new));
    private final JTextArea test = new JTextArea();
    private final JLabel output = new JLabel();
    private final JLabel status = new JLabel();
    private final JButton copy = new JButton("Copy generated test");
    private final Diagram diagram = new Diagram();
    RxjsMarblePanel(Consumer<String> clipboard) {
        super(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEADING));
        controls.add(new JLabel("Source:")); controls.add(input);
        controls.add(new JLabel("Operator:")); controls.add(operator);
        input.getAccessibleContext().setAccessibleName("Source marble timeline");
        input.setToolTipText("a = 1, b = 2, …; - = one frame; | = complete; # = error");
        operator.getAccessibleContext().setAccessibleName("Marble transformation");
        JPanel header = new JPanel(new BorderLayout(4, 4));
        header.add(controls, BorderLayout.NORTH);
        JLabel legend = new JLabel("<html><b>Legend:</b> - one frame · a–z values (a=1, b=2, …) · | complete · # error · (b|) value and completion in the same frame.<br>Edit the source to see the output and test update. This playground supports individual frames and synchronous operators.</html>");
        header.add(legend, BorderLayout.CENTER);
        add(header, BorderLayout.NORTH);
        test.setEditable(false);
        test.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        test.getAccessibleContext().setAccessibleName("Generated RxJS marble test");
        JPanel visualization = new JPanel(new BorderLayout(4, 4));
        visualization.add(new JScrollPane(diagram), BorderLayout.CENTER);
        output.setFont(new Font(Font.MONOSPACED, Font.BOLD, 13));
        visualization.add(output, BorderLayout.SOUTH);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, visualization, new JScrollPane(test));
        split.setResizeWeight(.4); split.setDividerLocation(205);
        add(split, BorderLayout.CENTER);
        JPanel footer = new JPanel(new BorderLayout(4, 4));
        footer.add(status, BorderLayout.CENTER); footer.add(copy, BorderLayout.EAST);
        add(footer, BorderLayout.SOUTH);
        copy.addActionListener(e -> clipboard.accept(test.getText()));
        input.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { refresh(); }
            @Override public void removeUpdate(DocumentEvent e) { refresh(); }
            @Override public void changedUpdate(DocumentEvent e) { refresh(); }
        });
        operator.addActionListener(e -> refresh());
        refresh();
    }
    private void refresh() {
        try {
            RxjsMarbles.Result result = RxjsMarbles.calculate(input.getText(), operator.getSelectedIndex());
            diagram.result = result;
            diagram.setPreferredSize(new Dimension(Math.max(530, 125 + result.input().length() * 34), 160));
            diagram.getAccessibleContext().setAccessibleDescription("Source " + result.input() + "; output " + result.output());
            output.setText("Expected: " + result.output());
            test.setText(result.test()); test.setCaretPosition(0);
            status.setForeground(UIManager.getColor("Label.foreground"));
            status.setText("1 frame = 1 ms in TestScheduler.run. Test uses Jasmine/Jest-style assertions.");
            copy.setEnabled(true);
        } catch (IllegalArgumentException ex) {
            diagram.result = null; output.setText("Expected: —"); test.setText("");
            status.setForeground(RxjsDocumentation.eventColor(3));
            status.setText(ex.getMessage()); copy.setEnabled(false);
        }
        diagram.revalidate(); diagram.repaint();
    }
    private static final class Diagram extends JPanel {
        private RxjsMarbles.Result result;
        Diagram() {
            setPreferredSize(new Dimension(530, 160));
            setToolTipText("Colors identify values; | marks completion and × marks an error. Labels show numeric values.");
            getAccessibleContext().setAccessibleName("Source and output marble diagrams");
        }
        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (result == null) return;
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int frames = Math.max(1, result.input().length() - 1);
                double unit = Math.max(34, (getWidth() - 145.0) / frames);
                g.setColor(getForeground());
                for (int f = 0; f <= frames; f++) {
                    int x = 105 + (int) (f * unit);
                    g.drawString(Integer.toString(f), x - 3, 20);
                }
                row(g, result.source(), "Source", 61, unit);
                row(g, result.result(), "Output", 122, unit);
            } finally { g.dispose(); }
        }
        private void row(Graphics2D g, List<RxjsMarbles.Event> events, String label, int y, double unit) {
            g.setColor(getForeground()); g.drawString(label, 12, y + 4);
            g.setStroke(new BasicStroke(1));
            g.drawLine(96, y, getWidth() - 16, y);
            for (RxjsMarbles.Event event : events) {
                int x = 105 + (int) (event.frame() * unit);
                if (event.symbol() == '|') {
                    g.setColor(getForeground());
                    boolean sameFrameValue = events.stream().anyMatch(e -> e.frame() == event.frame() && e.symbol() >= 'a' && e.symbol() <= 'z');
                    int offset = sameFrameValue ? 17 : 0;
                    g.setStroke(new BasicStroke(2)); g.drawLine(x + offset, y - 14, x + offset, y + 14);
                } else if (event.symbol() == '#') {
                    g.setColor(RxjsDocumentation.eventColor(3)); g.setStroke(new BasicStroke(3));
                    g.drawLine(x - 7, y - 7, x + 7, y + 7); g.drawLine(x - 7, y + 7, x + 7, y - 7);
                } else {
                    g.setColor(RxjsDocumentation.eventColor(event.symbol() - 'a'));
                    g.fillOval(x - 14, y - 14, 28, 28);
                    g.setColor(RxjsDocumentation.dark() ? Color.BLACK : Color.WHITE);
                    String symbol = String.valueOf(event.symbol());
                    g.drawString(symbol, x - g.getFontMetrics().stringWidth(symbol) / 2, y + 5);
                    g.setColor(getForeground()); g.drawString(event.label(), x - g.getFontMetrics().stringWidth(event.label()) / 2, y + 30);
                }
            }
        }
    }
}
