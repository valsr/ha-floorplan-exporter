/*
 * HA Floorplan Exporter, a Sweet Home 3D plugin
 * Copyright (c) 2026 valsr
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see <https://www.gnu.org/licenses/>.
 */
package io.github.valsr.hafloorplan.plugin;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.text.MessageFormat;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutionException;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;

import io.github.valsr.hafloorplan.engine.ExportException;
import io.github.valsr.hafloorplan.engine.ExportListener;
import io.github.valsr.hafloorplan.engine.Exporter;
import io.github.valsr.hafloorplan.plan.RenderJob;

/**
 * Runs an export in a background thread behind a dialog showing its progress and a button to cancel it.
 */
class ExportProgressDialog extends JDialog {
    private static final long serialVersionUID = 1L;
    private static final ResourceBundle MESSAGES = HaFloorplanPlugin.MESSAGES;

    private final Exporter exporter;
    private final File outputDir;
    private final JProgressBar progressBar = new JProgressBar();
    private final JLabel jobLabel = new JLabel(MESSAGES.getString("progress.starting"));
    private final JButton cancelButton = new JButton(MESSAGES.getString("button.cancel"));
    private int jobCount;

    ExportProgressDialog(Frame owner, Exporter exporter, File outputDir) {
        super(owner, MESSAGES.getString("progress.title"), true);
        this.exporter = exporter;
        this.outputDir = outputDir;

        this.progressBar.setIndeterminate(true);
        this.progressBar.setPreferredSize(new Dimension(420, this.progressBar.getPreferredSize().height));
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.TRAILING, 0, 0));
        buttonPanel.add(this.cancelButton);
        JPanel contentPanel = new JPanel(new BorderLayout(0, 8));
        contentPanel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        contentPanel.add(this.jobLabel, BorderLayout.PAGE_START);
        contentPanel.add(this.progressBar, BorderLayout.CENTER);
        contentPanel.add(buttonPanel, BorderLayout.PAGE_END);
        setContentPane(contentPanel);

        this.cancelButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ev) {
                cancel();
            }
        });
        // Closing the window cancels too, the dialog going away once the renderer stopped
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent ev) {
                cancel();
            }
        });
        pack();
        setLocationRelativeTo(owner);
    }

    private void cancel() {
        this.cancelButton.setEnabled(false);
        this.jobLabel.setText(MESSAGES.getString("progress.cancelling"));
        this.exporter.cancel();
    }

    /**
     * Runs the export and returns once it ended and the user was told how.
     */
    void run() {
        SwingWorker<Boolean, Object []> worker = new SwingWorker<Boolean, Object []>() {
            @Override
            protected Boolean doInBackground() throws ExportException {
                return ExportProgressDialog.this.exporter.run(new ExportListener() {
                    @Override
                    public void jobStarted(int index, int total, RenderJob job) {
                        publish(new Object [] {index, total, job.path});
                    }

                    @Override
                    public void jobFinished(int index, int total, RenderJob job) {
                    }
                });
            }

            @Override
            protected void process(List<Object []> jobs) {
                Object [] job = jobs.get(jobs.size() - 1);
                int index = (Integer)job [0];
                ExportProgressDialog.this.jobCount = (Integer)job [1];
                ExportProgressDialog.this.progressBar.setIndeterminate(false);
                ExportProgressDialog.this.progressBar.setMaximum(ExportProgressDialog.this.jobCount);
                ExportProgressDialog.this.progressBar.setValue(index);
                if (ExportProgressDialog.this.cancelButton.isEnabled()) {
                    ExportProgressDialog.this.jobLabel.setText(MessageFormat.format(
                            MESSAGES.getString("progress.job"), index + 1, ExportProgressDialog.this.jobCount, job [2]));
                }
            }

            @Override
            protected void done() {
                dispose();
            }
        };
        worker.execute();
        // Blocks until the worker disposes of this modal dialog
        setVisible(true);

        try {
            if (worker.get()) {
                JOptionPane.showMessageDialog(getOwner(),
                        MessageFormat.format(MESSAGES.getString("progress.done"), this.jobCount, this.outputDir),
                        MESSAGES.getString("progress.title"), JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(getOwner(), MESSAGES.getString("progress.cancelled"),
                        MESSAGES.getString("progress.title"), JOptionPane.WARNING_MESSAGE);
            }
        } catch (ExecutionException ex) {
            StringBuilder message = new StringBuilder(MESSAGES.getString("progress.failed"));
            if (ex.getCause() instanceof ExportException) {
                for (String problem : ((ExportException)ex.getCause()).getProblems()) {
                    message.append("\n• ").append(problem);
                }
            } else {
                message.append("\n").append(ex.getCause());
            }
            JOptionPane.showMessageDialog(getOwner(), message.toString(),
                    MESSAGES.getString("progress.title"), JOptionPane.ERROR_MESSAGE);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
