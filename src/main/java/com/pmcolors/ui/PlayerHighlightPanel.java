/*
 * Copyright (c) 2020, PresNL
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

/*
 * The Panel code has taken a lot of inspiration from the ScreenMarkers plugin by Psikoi so credits to him
 */

package com.pmcolors.ui;

import com.pmcolors.PMColorsPlugin;
import com.pmcolors.PlayerHighlight;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.components.colorpicker.RuneliteColorPicker;
import net.runelite.client.util.ImageUtil;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class PlayerHighlightPanel extends JPanel
{
    private final JLabel playerNameIndicator = new JLabel();
    private final JLabel highlightColorIndicator = new JLabel();
    private final JLabel deleteLabel = new JLabel();

    private PMColorsPlugin plugin;
    private PlayerHighlight playerHighlight;

    private static final ImageIcon DELETE_ICON;
    private static final ImageIcon DELETE_HOVER_ICON;

    private Color selectedColor;

    static
    {
        DELETE_ICON = new ImageIcon(ImageUtil.getResourceStreamFromClass(PMColorsPlugin.class, "/cancel_icon.png"));
        DELETE_HOVER_ICON = new ImageIcon(ImageUtil.alphaOffset(ImageUtil.bufferedImageFromImage(DELETE_ICON.getImage()), 0.6f));
    }

    PlayerHighlightPanel(PMColorsPlugin plugin, PlayerHighlight playerHighlight)
    {
        this.plugin = plugin;
        this.playerHighlight = playerHighlight;

        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(3, 5, 3, 5));
        setBackground(ColorScheme.DARKER_GRAY_COLOR);

        playerNameIndicator.setText(playerHighlight.getName());
        playerNameIndicator.setBorder(null);
        playerNameIndicator.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        playerNameIndicator.setPreferredSize(new Dimension(0, 24));
        playerNameIndicator.setForeground(Color.WHITE);

        highlightColorIndicator.setText(colorToHex(playerHighlight.getColor()));
        highlightColorIndicator.setOpaque(true);
        highlightColorIndicator.setPreferredSize(new Dimension(75, 24));
        highlightColorIndicator.setForeground(Color.WHITE);
        highlightColorIndicator.setBackground(playerHighlight.getColor().darker());
        highlightColorIndicator.setHorizontalAlignment(JLabel.CENTER);
        highlightColorIndicator.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mousePressed(MouseEvent mouseEvent)
            {
                openPlayerColorPicker();
            }

            @Override
            public void mouseEntered(MouseEvent mouseEvent)
            {
            }

            @Override
            public void mouseExited(MouseEvent mouseEvent)
            {
            }
        });

        deleteLabel.setIcon(DELETE_ICON);
        deleteLabel.setToolTipText("Delete player highlight");
        deleteLabel.setPreferredSize(new Dimension(14, 14));
        deleteLabel.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mousePressed(MouseEvent mouseEvent)
            {
                int confirm = JOptionPane.showConfirmDialog(PlayerHighlightPanel.this,
                        "Are you sure you want to permanently delete this player highlight?",
                        "Warning", JOptionPane.OK_CANCEL_OPTION);

                if (confirm == 0)
                {
                    plugin.deleteHighlight(playerHighlight);
                }
            }

            @Override
            public void mouseEntered(MouseEvent mouseEvent)
            {
                deleteLabel.setIcon(DELETE_HOVER_ICON);
            }

            @Override
            public void mouseExited(MouseEvent mouseEvent)
            {
                deleteLabel.setIcon(DELETE_ICON);
            }
        });

        add(playerNameIndicator, BorderLayout.CENTER);

        JPanel container = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        container.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        container.add(highlightColorIndicator);
        container.add(deleteLabel);

        add(container, BorderLayout.EAST);
    }
    private String colorToHex(Color color)
    {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }


    private void openPlayerColorPicker()
    {
        RuneliteColorPicker colorPicker = plugin.getColorPickerManager().create(
                SwingUtilities.windowForComponent(this),
                playerHighlight.getColor(),
                playerHighlight.getName() + " highlight color",
                true);
        colorPicker.setLocation(getLocationOnScreen());
        colorPicker.setOnColorChange(c ->
        {
            playerHighlight.setColor(c);
            highlightColorIndicator.setBackground(c.darker());
            highlightColorIndicator.setText(colorToHex(c));
        });
        colorPicker.setOnClose(c -> plugin.updateConfig());
        colorPicker.setVisible(true);
    }
}
