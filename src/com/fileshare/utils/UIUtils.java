package com.fileshare.utils;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * UI styling utility ensuring all buttons and components are visually high-contrast,
 * properly styled, and reactive across Windows, Mac, and Linux Look-and-Feels.
 */
public final class UIUtils {

    public static final Color PRIMARY_BLUE = new Color(25, 118, 210);
    public static final Color SUCCESS_GREEN = new Color(46, 125, 50);
    public static final Color DANGER_RED = new Color(211, 47, 47);
    public static final Color DARK_TEXT = new Color(33, 37, 41);

    private UIUtils() {
    }

    /**
     * Creates a prominent primary button with solid background, white text, and hover animations.
     */
    public static JButton createPrimaryButton(String text, Color bgColor, Color fgColor) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                if (!isEnabled()) {
                    g2.setColor(new Color(215, 219, 224));
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                    g2.setColor(new Color(150, 155, 160));
                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                } else {
                    if (getModel().isPressed()) {
                        g2.setColor(bgColor.darker());
                    } else if (getModel().isRollover()) {
                        g2.setColor(new Color(
                                Math.min(255, bgColor.getRed() + 25),
                                Math.min(255, bgColor.getGreen() + 25),
                                Math.min(255, bgColor.getBlue() + 25)
                        ));
                    } else {
                        g2.setColor(bgColor);
                    }
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                }
                g2.dispose();
                super.paintComponent(g);
            }
        };

        btn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btn.setForeground(fgColor);
        btn.setContentAreaFilled(false);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setOpaque(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setBorder(new EmptyBorder(8, 18, 8, 18));
        return btn;
    }

    /**
     * Creates a styled secondary action button with subtle outline and clear dark text.
     */
    public static JButton createSecondaryButton(String text) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                if (!isEnabled()) {
                    g2.setColor(new Color(240, 242, 245));
                    g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                    g2.setColor(new Color(205, 210, 215));
                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                } else {
                    if (getModel().isPressed()) {
                        g2.setColor(new Color(220, 224, 230));
                    } else if (getModel().isRollover()) {
                        g2.setColor(new Color(235, 239, 245));
                    } else {
                        g2.setColor(new Color(248, 249, 250));
                    }
                    g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                    g2.setColor(new Color(185, 190, 195));
                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                }
                g2.dispose();
                super.paintComponent(g);
            }
        };

        btn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        btn.setForeground(DARK_TEXT);
        btn.setContentAreaFilled(false);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setOpaque(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setBorder(new EmptyBorder(6, 14, 6, 14));
        return btn;
    }
}
