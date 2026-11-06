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

package org.netbeans.modules.editor.java;

import java.awt.*;
import java.util.List;
import javax.swing.*;
import javax.swing.text.JTextComponent;
import org.openide.awt.GraphicsUtils;
import org.openide.util.NbBundle;

/**
 *
 * @author  Dusan Balek
 */
public class MethodParamsTipPaintComponent extends JToolTip {

    private List<List<String>> attributes;
    private int activeAttribute;
    private boolean expanded;
    private final JLabel attributeView = new JLabel();
    private final JButton moreAttributes = new JButton();
    private final javax.swing.border.Border originalBorder;
    private final Color originalBackground;
    private final Color originalForeground;

    private static String html(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    void setAnnotationAttributes(List<List<String>> attributes, int active) {
        this.attributes = attributes;
        this.activeAttribute = active;
        removeAll();
        setBackground(originalBackground);
        setForeground(originalForeground);
        setBorder(originalBorder);
        if (attributes != null) {
            setBackground(component.getBackground());
            setForeground(component.getForeground());
            setLayout(new BorderLayout());
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(component.getForeground().darker()),
                    BorderFactory.createEmptyBorder(8, 12, 8, 12)));
            moreAttributes.setBackground(getBackground());
            moreAttributes.setForeground(getForeground());
            attributeView.setForeground(getForeground());
            attributeView.setFont(component.getFont());
            updateAttributes();
            add(attributeView, BorderLayout.CENTER);
            add(moreAttributes, BorderLayout.SOUTH);
        }
    }

    private void updateAttributes() {
        java.util.List<Integer> order = new java.util.ArrayList<>();
        for (int i = 0; i < attributes.size(); i++) {
            order.add(i);
        }
        java.util.List<String> common = java.util.Arrays.asList("value", "path", "produces", "consumes");
        order.sort(java.util.Comparator.comparingInt(i -> {
            int rank = common.indexOf(attributes.get(i).get(0));
            return rank >= 0 ? rank : 4;
        }));
        StringBuilder text = new StringBuilder("<html><b>Annotation attributes</b><table cellpadding='3'>");
        int hidden = 0;
        int position = 0;
        for (int i : order) {
            List<String> row = attributes.get(i);
            if (!expanded && position++ >= 4
                    && i != activeAttribute && !"Required".equals(row.get(2))) {
                hidden++;
                continue;
            }
            text.append("<tr><td>").append(i == activeAttribute ? "<b>▸ " : "")
                    .append(html(row.get(0))).append(i == activeAttribute ? "</b>" : "")
                    .append("</td><td>").append(html(row.get(1)))
                    .append("</td><td>").append(html(row.get(2))).append("</td></tr>");
        }
        text.append("</table>");
        if (activeAttribute >= 0 && activeAttribute < attributes.size()) {
            String description = attributes.get(activeAttribute).get(3);
            if (description.isEmpty()) {
                List<String> row = attributes.get(activeAttribute);
                description = "Required".equals(row.get(2)) ? "Required attribute." : "Optional attribute. Default: " + row.get(2);
            }
            text.append("<p style='width:340px'>").append(html(description)).append("</p>");
        }
        moreAttributes.setVisible(hidden > 0 || expanded && attributes.size() > 4);
        moreAttributes.setText(expanded ? "Fewer attributes" : "More attributes (+" + hidden + ")");
        text.append("<br><small>Ctrl+Space to select an attribute · Enter to insert</small></html>");
        attributeView.setText(text.toString());
        revalidate();
        repaint();
    }

    private int drawX;
    private int drawY;
    private int drawHeight;
    private int drawWidth;
    private Font drawFont;
    private int fontHeight;
    private int descent;
    private FontMetrics fontMetrics;

    private List<List<String>> params;
    private int idx;
    private final JTextComponent component;

    public MethodParamsTipPaintComponent(JTextComponent component){
        super();
        this.component = component;
        originalBorder = getBorder();
        originalBackground = getBackground();
        originalForeground = getForeground();
        moreAttributes.setFocusable(false);
        moreAttributes.addActionListener(event -> {
            expanded = !expanded;
            updateAttributes();
            setSize(getPreferredSize());
        });
    }
    
    void setData(List<List<String>> params, int idx) {
        this.params = params;
        this.idx = idx;
    }
    
    void clearData() {
        this.params = null;
        this.attributes = null;
        removeAll();
        this.idx = -1;
    }
    
    boolean hasData() {
        return params != null;
    }
    
    @Override
    public void paintComponent(Graphics g) {
        GraphicsUtils.configureDefaultRenderingHints(g);
        // clear background
        g.setColor(getBackground());
        Rectangle r = g.getClipBounds();
        g.fillRect(r.x, r.y, r.width, r.height);
        g.setColor(getForeground());
        if (attributes == null) {
            draw(g);
        }
    }

    protected void draw(Graphics g) {
        Insets in = getInsets();
        GraphicsConfiguration gc = component.getGraphicsConfiguration();
        int screenWidth = gc != null ? gc.getBounds().width : Integer.MAX_VALUE;
        if (in != null) {
            drawX = in.left;
            drawY = in.top;
        } else {
            drawX = 0;
            drawY = 0;
        }
        drawY += (fontHeight - descent);

        int startX = drawX;
        drawWidth = drawX;
        if (params != null) {
            for (List<String> p : params) {
                int i = 0;
                if (p.isEmpty()) {
                    p.add(NbBundle.getMessage(MethodParamsTipPaintComponent.class, "JCP-no-parameters"));
                }
                int plen = p.size() - 1;
                for (String s : p) {
                    if (i < plen) {
                        s += ", "; //NOI18N
                    }
                    if (getWidth(s, i == idx || i == plen && idx > plen ? getDrawFont().deriveFont(Font.BOLD) : getDrawFont()) + drawX > screenWidth) {
                        drawY += fontHeight;
                        drawX = startX + getWidth("        ", drawFont); //NOI18N
                    }
                    drawString(g, s, i == idx || i == plen && idx > plen ? getDrawFont().deriveFont(Font.BOLD) : getDrawFont());
                    if (drawWidth < drawX)
                        drawWidth = drawX;
                    i++;
                }
                drawY += fontHeight;
                drawX = startX;
            }
        }
        drawHeight = drawY - fontHeight + descent;
        if (in != null) {
            drawHeight += in.bottom;
            drawWidth += in.right;
        }
    }

    protected void drawString(Graphics g, String s, Font font) {
        if (g != null) {
            g.setFont(font);
            g.drawString(s, drawX, drawY);
            g.setFont(drawFont);
        }
        drawX += getWidth(s, font);
    }

    protected int getWidth(String s, Font font) {
        if (font == null) return fontMetrics.stringWidth(s);
        return getFontMetrics(font).stringWidth(s);
    }

    @Override
    public void setFont(Font font) {
        super.setFont(font);
        fontMetrics = this.getFontMetrics(font);
        fontHeight = fontMetrics.getHeight();
        
        descent = fontMetrics.getDescent();
        drawFont = font;
    }

    protected Font getDrawFont(){
        return drawFont;
    }

    @Override
    public Dimension getPreferredSize() {
        if (attributes != null) {
            Dimension size = attributeView.getPreferredSize();
            Insets in = getInsets();
            Dimension button = moreAttributes.isVisible() ? moreAttributes.getPreferredSize() : new Dimension();
            return new Dimension(Math.max(size.width, button.width) + in.left + in.right,
                    size.height + button.height + in.top + in.bottom);
        }
        draw(null);
        Insets i = getInsets();
        if (i != null) {
            drawX += i.right;
        }
        return new Dimension(drawWidth, drawHeight);
    }

}
