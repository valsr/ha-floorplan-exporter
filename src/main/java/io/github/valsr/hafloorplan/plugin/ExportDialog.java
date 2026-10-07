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
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.MessageFormat;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ResourceBundle;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;

import io.github.valsr.hafloorplan.plan.DateSchedule;
import io.github.valsr.hafloorplan.plan.ExportConfig;
import io.github.valsr.hafloorplan.plan.ExportPlanner;
import io.github.valsr.hafloorplan.plan.HomeSummary;
import io.github.valsr.hafloorplan.plan.Instructions;
import io.github.valsr.hafloorplan.plan.InstructionsException;
import io.github.valsr.hafloorplan.plan.InstructionsJson;
import io.github.valsr.hafloorplan.plan.InstructionsResolver;
import io.github.valsr.hafloorplan.plan.Quality;
import io.github.valsr.hafloorplan.plan.Ref;
import io.github.valsr.hafloorplan.plan.RenderJob;
import io.github.valsr.hafloorplan.plan.TimeSchedule;

/**
 * The dialog where the user chooses what to export. Its state is read and set as {@link Instructions},
 * the same value an instructions file holds.
 */
class ExportDialog extends JDialog {
    private static final long serialVersionUID = 1L;
    private static final ResourceBundle MESSAGES = HaFloorplanPlugin.MESSAGES;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final HomeSummary summary;
    private final File homeFile;
    private final boolean homeModified;

    private final List<FloorRow> floorRows = new ArrayList<FloorRow>();
    private final List<LightRow> lightRows = new ArrayList<LightRow>();
    final JTextField startDateField = new JTextField(8);
    final JTextField endDateField = new JTextField(8);
    private final JSpinner dateIntervalSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 3660, 1));
    final JTextField startTimeField = new JTextField(5);
    final JTextField endTimeField = new JTextField(5);
    private final JSpinner timeIntervalSpinner = new JSpinner(new SpinnerNumberModel(240, 1, 1440, 30));
    final JButton allLightsButton = new JButton(MESSAGES.getString("lights.all"));
    final JButton noLightsButton = new JButton(MESSAGES.getString("lights.none"));
    private final JSpinner widthSpinner = new JSpinner(new SpinnerNumberModel(1920, 1, 16384, 10));
    private final JSpinner heightSpinner = new JSpinner(new SpinnerNumberModel(1080, 1, 16384, 10));
    private final JComboBox<Item> rendererComboBox = new JComboBox<Item>();
    private final JComboBox<String> qualityComboBox = new JComboBox<String>(new String [] {
            MESSAGES.getString("options.quality.low"), MESSAGES.getString("options.quality.high")});
    private final JCheckBox hideCeilingsCheckBox = new JCheckBox(MESSAGES.getString("options.hideCeilings"), true);
    final JCheckBox isolateCheckBox = new JCheckBox(MESSAGES.getString("options.isolateLevel"));
    final JTextField outputField = new JTextField(30);
    private final JLabel summaryLabel = new JLabel(" ");
    private final JLabel errorLabel = new JLabel(" ");
    final JButton saveButton = new JButton(MESSAGES.getString("button.save"));
    private final JButton loadButton = new JButton(MESSAGES.getString("button.load"));
    final JButton exportButton = new JButton(MESSAGES.getString("button.export"));
    private final JButton cancelButton = new JButton(MESSAGES.getString("button.cancel"));

    /** Not shown in the dialog but kept from loaded instructions. */
    private int noiseThreshold = 6;
    private List<String> loadWarnings = Collections.emptyList();
    private boolean exportChosen;
    /** <code>true</code> while fields are set by program, to update the dialog only once afterwards. */
    private boolean updating;

    /**
     * @param homeFile the file of the edited home, or <code>null</code> if it was never saved
     * @param homeModified <code>true</code> if the home has changes that aren't in its file
     */
    ExportDialog(Frame owner, HomeSummary summary, File homeFile, boolean homeModified) {
        super(owner, MESSAGES.getString("dialog.title"), true);
        this.summary = summary;
        this.homeFile = homeFile;
        this.homeModified = homeModified;

        for (HomeSummary.Renderer renderer : summary.getRenderers()) {
            this.rendererComboBox.addItem(new Item(renderer.className, renderer.displayName));
        }
        // Sizes and intervals are shown without thousands separator
        for (JSpinner spinner : new JSpinner [] {this.widthSpinner, this.heightSpinner,
                                                this.dateIntervalSpinner, this.timeIntervalSpinner}) {
            spinner.setEditor(new JSpinner.NumberEditor(spinner, "#"));
        }
        String today = LocalDate.now().toString();
        this.startDateField.setText(today);
        this.endDateField.setText(today);
        this.startTimeField.setText("00:00");
        this.endTimeField.setText("23:00");
        this.isolateCheckBox.setToolTipText(MESSAGES.getString("options.isolateLevel.tooltip"));
        // Hiding other levels means nothing in a home without levels
        this.isolateCheckBox.setEnabled(summary.getFloors().size() != 1
                || !HomeSummary.DEFAULT_FLOOR_ID.equals(summary.getFloors().get(0).id));

        layoutComponents();
        addListeners();
        updateState();
        pack();
        setLocationRelativeTo(owner);
    }

    private void layoutComponents() {
        JPanel floorsPanel = new JPanel(new GridBagLayout());
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.anchor = GridBagConstraints.LINE_START;
        constraints.insets = new Insets(1, 2, 1, 8);
        for (HomeSummary.Floor floor : this.summary.getFloors()) {
            FloorRow row = new FloorRow(floor);
            this.floorRows.add(row);
            constraints.gridy++;
            constraints.gridx = 0;
            constraints.weightx = 1;
            floorsPanel.add(row.checkBox, constraints);
            constraints.gridx = 1;
            constraints.weightx = 0;
            floorsPanel.add(row.cameraComboBox, constraints);
        }

        JPanel lightsList = new JPanel();
        lightsList.setLayout(new BoxLayout(lightsList, BoxLayout.PAGE_AXIS));
        List<HomeSummary.Floor> lightFloors = new ArrayList<HomeSummary.Floor>(this.summary.getFloors());
        lightFloors.add(new HomeSummary.Floor(null, MESSAGES.getString("lights.noFloor")));
        for (HomeSummary.Floor floor : lightFloors) {
            boolean floorTitleAdded = false;
            for (HomeSummary.Light light : this.summary.getLights()) {
                if (floor.id == null ? light.floorId == null : floor.id.equals(light.floorId)) {
                    if (!floorTitleAdded) {
                        JLabel floorLabel = new JLabel(floor.name);
                        floorLabel.setFont(floorLabel.getFont().deriveFont(Font.BOLD));
                        floorLabel.setBorder(BorderFactory.createEmptyBorder(4, 2, 2, 2));
                        lightsList.add(floorLabel);
                        floorTitleAdded = true;
                    }
                    LightRow row = new LightRow(light);
                    this.lightRows.add(row);
                    lightsList.add(row.checkBox);
                }
            }
        }
        if (this.lightRows.isEmpty()) {
            lightsList.add(new JLabel(MESSAGES.getString("lights.empty")));
        }
        JScrollPane lightsScrollPane = new JScrollPane(lightsList);
        lightsScrollPane.setPreferredSize(new Dimension(260, 220));
        lightsScrollPane.getVerticalScrollBar().setUnitIncrement(16);
        JPanel lightButtons = new JPanel(new FlowLayout(FlowLayout.LEADING, 4, 2));
        lightButtons.add(this.allLightsButton);
        lightButtons.add(this.noLightsButton);
        JPanel lightsPanel = titled(new JPanel(new BorderLayout()), "lights.title");
        lightsPanel.add(lightsScrollPane, BorderLayout.CENTER);
        lightsPanel.add(lightButtons, BorderLayout.PAGE_END);

        JPanel optionsPanel = titled(new JPanel(new GridBagLayout()), "options.title");
        constraints = new GridBagConstraints();
        constraints.anchor = GridBagConstraints.LINE_START;
        constraints.insets = new Insets(2, 2, 2, 6);
        addOption(optionsPanel, constraints, "options.width", this.widthSpinner);
        addOption(optionsPanel, constraints, "options.height", this.heightSpinner);
        addOption(optionsPanel, constraints, "options.renderer", this.rendererComboBox);
        addOption(optionsPanel, constraints, "options.quality", this.qualityComboBox);
        constraints.gridx = 0;
        constraints.gridwidth = 2;
        constraints.gridy++;
        optionsPanel.add(this.hideCeilingsCheckBox, constraints);
        constraints.gridy++;
        optionsPanel.add(this.isolateCheckBox, constraints);

        JButton browseButton = new JButton(MESSAGES.getString("output.browse"));
        browseButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ev) {
                chooseOutputFolder();
            }
        });
        JPanel outputPanel = titled(new JPanel(new BorderLayout(4, 0)), "output.title");
        outputPanel.add(this.outputField, BorderLayout.CENTER);
        outputPanel.add(browseButton, BorderLayout.LINE_END);

        JPanel leftPanel = new JPanel();
        leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.PAGE_AXIS));
        JScrollPane floorsScrollPane = new JScrollPane(floorsPanel);
        floorsScrollPane.setBorder(null);
        JPanel floorsTitledPanel = titled(new JPanel(new BorderLayout()), "floors.title");
        floorsTitledPanel.add(floorsScrollPane, BorderLayout.CENTER);
        leftPanel.add(floorsTitledPanel);
        leftPanel.add(schedulePanel("dates.title", this.startDateField, this.endDateField, this.dateIntervalSpinner, "dates.unit"));
        leftPanel.add(schedulePanel("times.title", this.startTimeField, this.endTimeField, this.timeIntervalSpinner, "times.unit"));
        leftPanel.add(optionsPanel);

        JPanel centerPanel = new JPanel(new BorderLayout(6, 6));
        centerPanel.add(leftPanel, BorderLayout.CENTER);
        centerPanel.add(lightsPanel, BorderLayout.LINE_END);
        centerPanel.add(outputPanel, BorderLayout.PAGE_END);

        this.errorLabel.setForeground(new Color(0xB00020));
        JPanel statusPanel = new JPanel();
        statusPanel.setLayout(new BoxLayout(statusPanel, BoxLayout.PAGE_AXIS));
        statusPanel.add(this.summaryLabel);
        statusPanel.add(this.errorLabel);

        JPanel buttonsPanel = new JPanel();
        buttonsPanel.setLayout(new BoxLayout(buttonsPanel, BoxLayout.LINE_AXIS));
        buttonsPanel.add(this.loadButton);
        buttonsPanel.add(Box.createHorizontalStrut(4));
        buttonsPanel.add(this.saveButton);
        buttonsPanel.add(Box.createHorizontalGlue());
        buttonsPanel.add(this.cancelButton);
        buttonsPanel.add(Box.createHorizontalStrut(4));
        buttonsPanel.add(this.exportButton);

        JPanel bottomPanel = new JPanel(new BorderLayout(0, 6));
        bottomPanel.add(statusPanel, BorderLayout.PAGE_START);
        bottomPanel.add(buttonsPanel, BorderLayout.PAGE_END);

        JPanel contentPanel = new JPanel(new BorderLayout(6, 6));
        contentPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        contentPanel.add(centerPanel, BorderLayout.CENTER);
        contentPanel.add(bottomPanel, BorderLayout.PAGE_END);
        setContentPane(contentPanel);
        getRootPane().setDefaultButton(this.exportButton);
    }

    private static <T extends JComponent> T titled(T component, String titleKey) {
        component.setBorder(BorderFactory.createTitledBorder(MESSAGES.getString(titleKey)));
        return component;
    }

    private static void addOption(JPanel panel, GridBagConstraints constraints, String labelKey, JComponent component) {
        constraints.gridy++;
        constraints.gridx = 0;
        constraints.weightx = 0;
        panel.add(new JLabel(MESSAGES.getString(labelKey)), constraints);
        constraints.gridx = 1;
        constraints.weightx = 1;
        panel.add(component, constraints);
    }

    private static JPanel schedulePanel(String titleKey, JTextField startField, JTextField endField,
                                        JSpinner intervalSpinner, String unitKey) {
        JPanel panel = titled(new JPanel(new FlowLayout(FlowLayout.LEADING, 4, 2)), titleKey);
        panel.add(new JLabel(MESSAGES.getString("schedule.start")));
        panel.add(startField);
        panel.add(new JLabel(MESSAGES.getString("schedule.end")));
        panel.add(endField);
        panel.add(new JLabel(MESSAGES.getString("schedule.every")));
        panel.add(intervalSpinner);
        panel.add(new JLabel(MESSAGES.getString(unitKey)));
        return panel;
    }

    private void addListeners() {
        final ActionListener actionListener = new ActionListener() {
            public void actionPerformed(ActionEvent ev) {
                updateState();
            }
        };
        ChangeListener changeListener = new ChangeListener() {
            public void stateChanged(ChangeEvent ev) {
                updateState();
            }
        };
        DocumentListener documentListener = new DocumentListener() {
            public void insertUpdate(DocumentEvent ev) {
                updateState();
            }

            public void removeUpdate(DocumentEvent ev) {
                updateState();
            }

            public void changedUpdate(DocumentEvent ev) {
                updateState();
            }
        };
        for (FloorRow row : this.floorRows) {
            row.checkBox.addActionListener(actionListener);
            row.cameraComboBox.addActionListener(actionListener);
        }
        for (LightRow row : this.lightRows) {
            row.checkBox.addActionListener(actionListener);
        }
        for (JTextField field : new JTextField [] {this.startDateField, this.endDateField,
                                                  this.startTimeField, this.endTimeField, this.outputField}) {
            field.getDocument().addDocumentListener(documentListener);
        }
        this.dateIntervalSpinner.addChangeListener(changeListener);
        this.timeIntervalSpinner.addChangeListener(changeListener);

        this.allLightsButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ev) {
                selectAllLights(true);
            }
        });
        this.noLightsButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ev) {
                selectAllLights(false);
            }
        });
        this.saveButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ev) {
                chooseFileAndSave();
            }
        });
        this.loadButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ev) {
                chooseFileAndLoad();
            }
        });
        this.exportButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ev) {
                ExportDialog.this.exportChosen = true;
                dispose();
            }
        });
        this.cancelButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ev) {
                dispose();
            }
        });
    }

    private void selectAllLights(boolean selected) {
        for (LightRow row : this.lightRows) {
            row.checkBox.setSelected(selected);
        }
        updateState();
    }

    /**
     * Shows this dialog and returns <code>true</code> if the user chose to export.
     */
    boolean showDialog() {
        this.exportChosen = false;
        setVisible(true);
        return this.exportChosen;
    }

    /**
     * Returns what prevents exporting with the current choices, an empty list if nothing does.
     */
    List<String> getValidationErrors() {
        List<String> errors = new ArrayList<String>();
        buildInstructions(errors);
        boolean floorSelected = false;
        for (FloorRow row : this.floorRows) {
            floorSelected |= row.checkBox.isSelected();
        }
        if (!floorSelected) {
            errors.add(MESSAGES.getString("error.noFloor"));
        }
        if (this.outputField.getText().trim().isEmpty()) {
            errors.add(MESSAGES.getString("error.noOutput"));
        }
        return errors;
    }

    /**
     * Returns the choices of the user, where floors, cameras and lights have both their id and name.
     * @throws IllegalStateException if dates or times are invalid
     */
    Instructions getInstructions() {
        List<String> errors = new ArrayList<String>();
        Instructions instructions = buildInstructions(errors);
        if (instructions == null) {
            throw new IllegalStateException(errors.toString());
        }
        return instructions;
    }

    /**
     * Returns the instructions matching the fields, or <code>null</code> after adding
     * to <code>errors</code> why dates or times can't be used.
     */
    private Instructions buildInstructions(List<String> errors) {
        DateSchedule dates = null;
        LocalDate startDate = parseDate(this.startDateField, "error.startDate", errors);
        LocalDate endDate = parseDate(this.endDateField, "error.endDate", errors);
        if (startDate != null && endDate != null) {
            try {
                dates = new DateSchedule(startDate, endDate, (Integer)this.dateIntervalSpinner.getValue());
            } catch (IllegalArgumentException ex) {
                errors.add(ex.getMessage());
            }
        }
        TimeSchedule times = null;
        LocalTime startTime = parseTime(this.startTimeField, "error.startTime", errors);
        LocalTime endTime = parseTime(this.endTimeField, "error.endTime", errors);
        if (startTime != null && endTime != null) {
            try {
                times = new TimeSchedule(startTime, endTime, (Integer)this.timeIntervalSpinner.getValue());
            } catch (IllegalArgumentException ex) {
                errors.add(ex.getMessage());
            }
        }
        if (dates == null || times == null) {
            return null;
        }

        List<Instructions.Floor> floors = new ArrayList<Instructions.Floor>();
        for (FloorRow row : this.floorRows) {
            if (row.checkBox.isSelected()) {
                Item camera = (Item)row.cameraComboBox.getSelectedItem();
                floors.add(new Instructions.Floor(new Ref(row.floor.id, row.floor.name),
                        camera.id != null ? new Ref(camera.id, camera.label) : null));
            }
        }
        List<Ref> lights = new ArrayList<Ref>();
        for (LightRow row : this.lightRows) {
            if (row.checkBox.isSelected()) {
                lights.add(new Ref(row.light.id, row.light.name));
            }
        }
        Item renderer = (Item)this.rendererComboBox.getSelectedItem();
        String output = this.outputField.getText().trim();
        return Instructions.builder()
                .home(this.homeFile != null ? this.homeFile.getAbsolutePath() : null)
                .output(output.isEmpty() ? null : new File(output).getAbsolutePath())
                .floors(floors)
                .dates(dates)
                .times(times)
                .lights(lights)
                .width((Integer)this.widthSpinner.getValue())
                .height((Integer)this.heightSpinner.getValue())
                .renderer(renderer != null ? renderer.id : null)
                .quality(this.qualityComboBox.getSelectedIndex() == 1 ? Quality.HIGH : Quality.LOW)
                .hideCeilings(this.hideCeilingsCheckBox.isSelected())
                .isolateLevel(this.isolateCheckBox.isEnabled() && this.isolateCheckBox.isSelected())
                .noiseThreshold(this.noiseThreshold)
                .build();
    }

    private static LocalDate parseDate(JTextField field, String errorKey, List<String> errors) {
        try {
            return LocalDate.parse(field.getText().trim());
        } catch (DateTimeParseException ex) {
            errors.add(MESSAGES.getString(errorKey));
            return null;
        }
    }

    private static LocalTime parseTime(JTextField field, String errorKey, List<String> errors) {
        try {
            return LocalTime.parse(field.getText().trim(), TIME_FORMAT);
        } catch (DateTimeParseException ex) {
            errors.add(MESSAGES.getString(errorKey));
            return null;
        }
    }

    /**
     * Replaces the choices shown by <code>instructions</code>. What they designate that the home doesn't have
     * is left out and listed by {@link #getLoadWarnings()}.
     */
    void setInstructions(Instructions instructions) {
        File baseDir = this.homeFile != null ? this.homeFile.getAbsoluteFile().getParentFile() : new File("").getAbsoluteFile();
        InstructionsResolver.Resolution resolution = InstructionsResolver.resolve(instructions, this.summary, baseDir);
        ExportConfig config = resolution.getConfig();
        this.loadWarnings = resolution.getProblems();

        this.updating = true;
        try {
            for (FloorRow row : this.floorRows) {
                row.checkBox.setSelected(false);
                row.cameraComboBox.setSelectedIndex(0);
                for (ExportConfig.Floor floor : config.getFloors()) {
                    if (floor.levelId.equals(row.floor.id)) {
                        row.checkBox.setSelected(true);
                        selectItem(row.cameraComboBox, floor.cameraId);
                    }
                }
            }
            for (LightRow row : this.lightRows) {
                row.checkBox.setSelected(config.getLightIds().contains(row.light.id));
            }
            this.startDateField.setText(config.getDates().getStart().toString());
            this.endDateField.setText(config.getDates().getEnd().toString());
            this.dateIntervalSpinner.setValue(config.getDates().getIntervalDays());
            this.startTimeField.setText(config.getTimes().getStart().format(TIME_FORMAT));
            this.endTimeField.setText(config.getTimes().getEnd().format(TIME_FORMAT));
            this.timeIntervalSpinner.setValue(config.getTimes().getIntervalMinutes());
            this.widthSpinner.setValue(config.getWidth());
            this.heightSpinner.setValue(config.getHeight());
            selectItem(this.rendererComboBox, config.getRendererClassName());
            this.qualityComboBox.setSelectedIndex(config.getQuality() == Quality.HIGH ? 1 : 0);
            this.hideCeilingsCheckBox.setSelected(config.isHideCeilings());
            this.isolateCheckBox.setSelected(config.isIsolateLevel());
            this.noiseThreshold = config.getNoiseThreshold();
            this.outputField.setText(config.getOutputDir() != null ? config.getOutputDir().getPath() : "");
        } finally {
            this.updating = false;
        }
        updateState();
    }

    private static void selectItem(JComboBox<Item> comboBox, String id) {
        for (int i = 0; i < comboBox.getItemCount(); i++) {
            String itemId = comboBox.getItemAt(i).id;
            if (itemId == null ? id == null : itemId.equals(id)) {
                comboBox.setSelectedIndex(i);
            }
        }
    }

    /**
     * Returns what the instructions last given to {@link #setInstructions} designated that the home doesn't have.
     */
    List<String> getLoadWarnings() {
        return this.loadWarnings;
    }

    /**
     * Returns the count of images the current choices produce, or an empty text if they're invalid.
     */
    String getSummaryText() {
        Instructions instructions = buildInstructions(new ArrayList<String>());
        if (instructions == null) {
            return "";
        }
        ExportConfig config = InstructionsResolver.resolve(instructions, this.summary, null).getConfig();
        int baseCount = 0;
        int lightCount = 0;
        for (RenderJob job : ExportPlanner.plan(config, this.summary).getJobs()) {
            if (job.kind == RenderJob.Kind.BASE) {
                baseCount++;
            } else if (job.kind == RenderJob.Kind.LIGHT) {
                lightCount++;
            }
        }
        return MessageFormat.format(MESSAGES.getString("summary"), baseCount, lightCount);
    }

    private void updateState() {
        if (this.updating) {
            return;
        }
        for (FloorRow row : this.floorRows) {
            row.cameraComboBox.setEnabled(row.checkBox.isSelected());
        }
        List<String> errors = getValidationErrors();
        this.errorLabel.setText(errors.isEmpty() ? " " : errors.get(0));
        String summaryText = getSummaryText();
        this.summaryLabel.setText(summaryText.isEmpty() ? " " : summaryText);
        // A saved file must be runnable, so saving needs the same valid choices as exporting
        this.exportButton.setEnabled(errors.isEmpty());
        this.saveButton.setEnabled(errors.isEmpty());
    }

    private void chooseOutputFolder() {
        JFileChooser chooser = new JFileChooser(this.outputField.getText().trim().isEmpty()
                ? (this.homeFile != null ? this.homeFile.getParentFile() : null)
                : new File(this.outputField.getText().trim()));
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            this.outputField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private JFileChooser createInstructionsChooser() {
        JFileChooser chooser = new JFileChooser(this.homeFile != null ? this.homeFile.getParentFile() : null);
        chooser.setFileFilter(new FileNameExtensionFilter(MESSAGES.getString("instructions.fileFilter"), "json"));
        return chooser;
    }

    private void chooseFileAndSave() {
        JFileChooser chooser = createInstructionsChooser();
        String homeName = this.homeFile != null ? this.homeFile.getName().replaceFirst("\\.[^.]*$", "") : "home";
        chooser.setSelectedFile(new File(chooser.getCurrentDirectory(), homeName + ".ha-floorplan.json"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        try {
            saveInstructions(file);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, MessageFormat.format(MESSAGES.getString("save.failed"), file, ex.getMessage()),
                    MESSAGES.getString("save.title"), JOptionPane.ERROR_MESSAGE);
            return;
        }
        String message = MessageFormat.format(MESSAGES.getString("save.done"), file);
        // The command line reads the home from its file, not from this window
        if (this.homeFile == null) {
            message += MESSAGES.getString("save.homeNeverSaved");
        } else if (this.homeModified) {
            message += MESSAGES.getString("save.homeUnsaved");
        }
        JOptionPane.showMessageDialog(this, message, MESSAGES.getString("save.title"), JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Writes the current choices in an instructions file.
     */
    void saveInstructions(File file) throws IOException {
        Files.write(file.toPath(), InstructionsJson.write(getInstructions()).getBytes(StandardCharsets.UTF_8));
    }

    private void chooseFileAndLoad() {
        JFileChooser chooser = createInstructionsChooser();
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        List<String> problems;
        try {
            problems = loadInstructions(file);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, MessageFormat.format(MESSAGES.getString("load.failed"), file, ex.getMessage()),
                    MESSAGES.getString("load.title"), JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (!problems.isEmpty()) {
            StringBuilder message = new StringBuilder(MESSAGES.getString("load.problems"));
            for (String problem : problems) {
                message.append("\n• ").append(problem);
            }
            JOptionPane.showMessageDialog(this, message.toString(), MESSAGES.getString("load.title"), JOptionPane.WARNING_MESSAGE);
        }
    }

    /**
     * Replaces the current choices by the ones of an instructions file. The home named by the file is ignored.
     * @return the problems of an invalid file, in which case nothing changes, or what the file designates
     *     that the home doesn't have
     */
    List<String> loadInstructions(File file) throws IOException {
        Instructions instructions;
        try {
            instructions = InstructionsJson.parse(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
        } catch (InstructionsException ex) {
            return ex.getProblems();
        }
        if (instructions.getOutput() != null && !new File(instructions.getOutput()).isAbsolute()) {
            // As on the command line, a relative output folder starts from the folder of the file
            instructions = instructions.withOutput(
                    new File(file.getAbsoluteFile().getParentFile(), instructions.getOutput()).getPath());
        }
        setInstructions(instructions);
        return getLoadWarnings();
    }

    /** A floor with the camera it's seen from. */
    private final class FloorRow {
        final HomeSummary.Floor floor;
        final JCheckBox checkBox;
        final JComboBox<Item> cameraComboBox = new JComboBox<Item>();

        FloorRow(HomeSummary.Floor floor) {
            this.floor = floor;
            this.checkBox = new JCheckBox(floor.name, true);
            this.cameraComboBox.addItem(new Item(null, MESSAGES.getString("floors.currentView")));
            for (HomeSummary.Camera camera : ExportDialog.this.summary.getCameras()) {
                this.cameraComboBox.addItem(new Item(camera.id, camera.name));
            }
        }
    }

    private static final class LightRow {
        final HomeSummary.Light light;
        final JCheckBox checkBox;

        LightRow(HomeSummary.Light light) {
            this.light = light;
            this.checkBox = new JCheckBox(light.name, true);
        }
    }

    /** A choice of a combo box. */
    private static final class Item {
        final String id;
        final String label;

        Item(String id, String label) {
            this.id = id;
            this.label = label;
        }

        @Override
        public String toString() {
            return this.label;
        }
    }
}
