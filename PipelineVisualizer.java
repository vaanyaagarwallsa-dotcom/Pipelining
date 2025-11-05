import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

public class PipelineVisualizer extends JFrame {
    private JTextArea instructionInput;
    private JTable pipelineTable, sequentialTable;
    private JLabel hazardCountLabel, cycleComparisonLabel, currentCycleLabel;
    private JTextArea hazardDetailsArea, currentStatusArea;
    private DefaultTableModel pipelineModel, sequentialModel;
    private JButton nextBtn, prevBtn, playBtn, resetBtn;
    private JSlider speedSlider;
    
    private final String[] stages = {"IF", "ID", "EX", "MEM", "WB"};
    private List<Instruction> instructions;
    private List<Hazard> detectedHazards;
    private int totalCyclesPipeline, totalCyclesSequential;
    
    // Step-by-step simulation variables
    private int currentCycle = 0;
    private boolean isPlaying = false;
    private javax.swing.Timer animationTimer;  // Explicitly specify javax.swing.Timer
    private Map<Integer, String> cycleEvents;
    private int[] instructionStartCycle;
    
    public PipelineVisualizer() {
        setTitle("Step-by-Step Instruction Pipeline Visualizer with Hazard Detection");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));
        setSize(1400, 900);
        
        instructions = new ArrayList<>();
        detectedHazards = new ArrayList<>();
        cycleEvents = new HashMap<>();
        
        createComponents();
        setLocationRelativeTo(null);
        setVisible(true);
    }
    
    private void createComponents() {
        // Top panel - Instructions Input
        JPanel topPanel = new JPanel(new BorderLayout(5, 5));
        topPanel.setBorder(BorderFactory.createTitledBorder("Instructions (Format: OPERATION DEST,SRC1,SRC2)"));
        
        instructionInput = new JTextArea(5, 40);
        instructionInput.setText("ADD R1,R2,R3\nSUB R4,R1,R5\nAND R6,R1,R7\nOR R8,R6,R9\nXOR R10,R4,R11");
        instructionInput.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane inputScroll = new JScrollPane(instructionInput);
        topPanel.add(inputScroll, BorderLayout.CENTER);
        
        JButton simulateBtn = new JButton("Load Instructions");
        simulateBtn.setFont(new Font("Arial", Font.BOLD, 14));
        simulateBtn.addActionListener(e -> initializeSimulation());
        topPanel.add(simulateBtn, BorderLayout.EAST);
        
        add(topPanel, BorderLayout.NORTH);
        
        // Center panel - Pipeline and Sequential Tables with Controls
        JPanel centerPanel = new JPanel(new BorderLayout(5, 5));
        
        // Control Panel
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
        controlPanel.setBorder(BorderFactory.createTitledBorder("Simulation Controls"));
        
        resetBtn = new JButton("⟲ Reset");
        prevBtn = new JButton("◄ Previous");
        nextBtn = new JButton("Next ►");
        playBtn = new JButton("▶ Play");
        
        resetBtn.setFont(new Font("Arial", Font.BOLD, 12));
        prevBtn.setFont(new Font("Arial", Font.BOLD, 12));
        nextBtn.setFont(new Font("Arial", Font.BOLD, 12));
        playBtn.setFont(new Font("Arial", Font.BOLD, 12));
        
        resetBtn.addActionListener(e -> resetSimulation());
        prevBtn.addActionListener(e -> previousCycle());
        nextBtn.addActionListener(e -> nextCycle());
        playBtn.addActionListener(e -> togglePlay());
        
        currentCycleLabel = new JLabel("Cycle: 0 / 0");
        currentCycleLabel.setFont(new Font("Arial", Font.BOLD, 16));
        currentCycleLabel.setForeground(new Color(0, 0, 150));
        
        JLabel speedLabel = new JLabel("Speed:");
        speedSlider = new JSlider(1, 10, 5);
        speedSlider.setPreferredSize(new Dimension(150, 30));
        speedSlider.setMajorTickSpacing(3);
        speedSlider.setPaintTicks(true);
        
        controlPanel.add(resetBtn);
        controlPanel.add(prevBtn);
        controlPanel.add(playBtn);
        controlPanel.add(nextBtn);
        controlPanel.add(Box.createHorizontalStrut(20));
        controlPanel.add(currentCycleLabel);
        controlPanel.add(Box.createHorizontalStrut(20));
        controlPanel.add(speedLabel);
        controlPanel.add(speedSlider);
        
        centerPanel.add(controlPanel, BorderLayout.NORTH);
        
        // Tables Panel
        JPanel tablesPanel = new JPanel(new GridLayout(2, 1, 5, 5));
        
        // Pipeline execution table
        JPanel pipelinePanel = new JPanel(new BorderLayout());
        pipelinePanel.setBorder(BorderFactory.createTitledBorder("Pipelined Execution (with Hazard Handling)"));
        pipelineModel = new DefaultTableModel();
        pipelineTable = new JTable(pipelineModel);
        pipelineTable.setFont(new Font("Monospaced", Font.PLAIN, 11));
        pipelineTable.setRowHeight(25);
        JScrollPane pipelineScroll = new JScrollPane(pipelineTable);
        pipelinePanel.add(pipelineScroll, BorderLayout.CENTER);
        tablesPanel.add(pipelinePanel);
        
        // Sequential execution table
        JPanel sequentialPanel = new JPanel(new BorderLayout());
        sequentialPanel.setBorder(BorderFactory.createTitledBorder("Sequential Execution (No Pipeline)"));
        sequentialModel = new DefaultTableModel();
        sequentialTable = new JTable(sequentialModel);
        sequentialTable.setFont(new Font("Monospaced", Font.PLAIN, 11));
        sequentialTable.setRowHeight(25);
        JScrollPane sequentialScroll = new JScrollPane(sequentialTable);
        sequentialPanel.add(sequentialScroll, BorderLayout.CENTER);
        tablesPanel.add(sequentialPanel);
        
        centerPanel.add(tablesPanel, BorderLayout.CENTER);
        add(centerPanel, BorderLayout.CENTER);
        
        // Bottom panel - Statistics and Status
        JPanel bottomPanel = new JPanel(new BorderLayout(5, 5));
        
        JPanel statsPanel = new JPanel(new GridLayout(2, 1, 5, 5));
        hazardCountLabel = new JLabel("Hazards Detected: 0");
        hazardCountLabel.setFont(new Font("Arial", Font.BOLD, 14));
        hazardCountLabel.setForeground(Color.RED);
        cycleComparisonLabel = new JLabel("Pipeline: 0 cycles | Sequential: 0 cycles | Speedup: 0.00x");
        cycleComparisonLabel.setFont(new Font("Arial", Font.BOLD, 14));
        cycleComparisonLabel.setForeground(new Color(0, 100, 0));
        statsPanel.add(hazardCountLabel);
        statsPanel.add(cycleComparisonLabel);
        bottomPanel.add(statsPanel, BorderLayout.NORTH);
        
        JPanel detailsPanel = new JPanel(new GridLayout(1, 2, 5, 5));
        
        currentStatusArea = new JTextArea(6, 40);
        currentStatusArea.setFont(new Font("Monospaced", Font.BOLD, 12));
        currentStatusArea.setEditable(false);
        currentStatusArea.setBackground(new Color(255, 255, 200));
        JScrollPane statusScroll = new JScrollPane(currentStatusArea);
        statusScroll.setBorder(BorderFactory.createTitledBorder("Current Cycle Status"));
        detailsPanel.add(statusScroll);
        
        hazardDetailsArea = new JTextArea(6, 40);
        hazardDetailsArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        hazardDetailsArea.setEditable(false);
        JScrollPane hazardScroll = new JScrollPane(hazardDetailsArea);
        hazardScroll.setBorder(BorderFactory.createTitledBorder("All Hazard Details"));
        detailsPanel.add(hazardScroll);
        
        bottomPanel.add(detailsPanel, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);
        
        // Initialize animation timer - explicitly use javax.swing.Timer
        animationTimer = new javax.swing.Timer(1000, e -> nextCycle());
        
        // Disable controls initially
        setControlsEnabled(false);
    }
    
    private void initializeSimulation() {
        parseInstructions();
        if (instructions.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter valid instructions!");
            return;
        }
        
        stopAnimation();
        detectedHazards.clear();
        cycleEvents.clear();
        detectDataHazards();
        
        setupPipelinedExecution();
        setupSequentialExecution();
        displayHazardDetails();
        updateStatistics();
        
        currentCycle = 0;
        updateDisplay();
        setControlsEnabled(true);
    }
    
    private void setControlsEnabled(boolean enabled) {
        nextBtn.setEnabled(enabled);
        prevBtn.setEnabled(enabled);
        playBtn.setEnabled(enabled);
        resetBtn.setEnabled(enabled);
    }
    
    private void togglePlay() {
        if (isPlaying) {
            stopAnimation();
        } else {
            startAnimation();
        }
    }
    
    private void startAnimation() {
        isPlaying = true;
        playBtn.setText("⏸ Pause");
        int delay = 1100 - (speedSlider.getValue() * 100); // Faster = smaller delay
        animationTimer.setDelay(delay);
        animationTimer.start();
    }
    
    private void stopAnimation() {
        isPlaying = false;
        playBtn.setText("▶ Play");
        animationTimer.stop();
    }
    
    private void resetSimulation() {
        stopAnimation();
        currentCycle = 0;
        updateDisplay();
    }
    
    private void nextCycle() {
        if (currentCycle < Math.max(totalCyclesPipeline, totalCyclesSequential)) {
            currentCycle++;
            updateDisplay();
        } else {
            stopAnimation();
        }
    }
    
    private void previousCycle() {
        if (currentCycle > 0) {
            currentCycle--;
            updateDisplay();
        }
    }
    
    private void updateDisplay() {
        currentCycleLabel.setText(String.format("Cycle: %d / %d", 
            currentCycle, Math.max(totalCyclesPipeline, totalCyclesSequential)));
        
        // Update current status
        updateCurrentStatus();
        
        // Refresh table renderers
        pipelineTable.setDefaultRenderer(Object.class, 
            new PipelineRenderer(detectedHazards, instructionStartCycle, currentCycle));
        sequentialTable.setDefaultRenderer(Object.class, 
            new SequentialRenderer(currentCycle));
        
        pipelineTable.repaint();
        sequentialTable.repaint();
    }
    
    private void updateCurrentStatus() {
        StringBuilder status = new StringBuilder();
        status.append("═══ CYCLE ").append(currentCycle).append(" ═══\n\n");
        
        if (currentCycle == 0) {
            status.append("Ready to start simulation.\nClick 'Next' or 'Play' to begin.\n");
        } else {
            // Pipeline status
            status.append("▶ PIPELINE:\n");
            boolean pipelineActive = false;
            for (int i = 0; i < instructions.size(); i++) {
                int startCycle = instructionStartCycle[i];
                int endCycle = startCycle + stages.length - 1;
                
                if (currentCycle >= startCycle && currentCycle <= endCycle) {
                    int stageIndex = currentCycle - startCycle;
                    status.append(String.format("  I%d: %s stage\n", i, stages[stageIndex]));
                    pipelineActive = true;
                }
            }
            if (!pipelineActive && currentCycle <= totalCyclesPipeline) {
                status.append("  (stall or idle)\n");
            } else if (currentCycle > totalCyclesPipeline) {
                status.append("  COMPLETED!\n");
            }
            
            // Check for hazards at this cycle
            status.append("\n▶ HAZARDS:\n");
            boolean hazardFound = false;
            for (Hazard h : detectedHazards) {
                int hazardCycle = instructionStartCycle[h.currentInstrIndex];
                if (currentCycle == hazardCycle - 1) {
                    status.append(String.format("  ⚠ %s HAZARD DETECTED!\n", h.type));
                    status.append(String.format("  I%d needs %s (written by I%d)\n", 
                        h.currentInstrIndex, h.prevInstr.dest, h.prevInstrIndex));
                    status.append(String.format("  → Inserting %d stall cycle(s)\n", h.stallCycles));
                    hazardFound = true;
                }
            }
            if (!hazardFound) {
                status.append("  No hazards at this cycle\n");
            }
            
            // Sequential status
            status.append("\n▶ SEQUENTIAL:\n");
            boolean sequentialActive = false;
            for (int i = 0; i < instructions.size(); i++) {
                int startCycle = i * stages.length + 1;
                int endCycle = startCycle + stages.length - 1;
                
                if (currentCycle >= startCycle && currentCycle <= endCycle) {
                    int stageIndex = currentCycle - startCycle;
                    status.append(String.format("  I%d: %s stage\n", i, stages[stageIndex]));
                    sequentialActive = true;
                    break; // Only one instruction at a time
                }
            }
            if (!sequentialActive && currentCycle <= totalCyclesSequential) {
                status.append("  (idle)\n");
            } else if (currentCycle > totalCyclesSequential) {
                status.append("  COMPLETED!\n");
            }
            
            // Speed comparison
            if (currentCycle > totalCyclesPipeline && currentCycle <= totalCyclesSequential) {
                int difference = currentCycle - totalCyclesPipeline;
                status.append(String.format("\n⚡ Pipeline finished %d cycle(s) ago!\n", difference));
            }
        }
        
        currentStatusArea.setText(status.toString());
        currentStatusArea.setCaretPosition(0);
    }
    
    private void parseInstructions() {
        instructions.clear();
        String[] lines = instructionInput.getText().trim().split("\n");
        
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            
            String[] parts = line.split("\\s+", 2);
            if (parts.length < 2) continue;
            
            String operation = parts[0];
            String[] operands = parts[1].split(",");
            
            String dest = operands.length > 0 ? operands[0].trim() : "";
            String src1 = operands.length > 1 ? operands[1].trim() : "";
            String src2 = operands.length > 2 ? operands[2].trim() : "";
            
            instructions.add(new Instruction(i, operation, dest, src1, src2));
        }
    }
    
    private void detectDataHazards() {
        for (int i = 0; i < instructions.size(); i++) {
            Instruction current = instructions.get(i);
            
            for (int j = i - 1; j >= Math.max(0, i - 3); j--) {
                Instruction prev = instructions.get(j);
                
                if (!prev.dest.isEmpty() && 
                    (prev.dest.equals(current.src1) || prev.dest.equals(current.src2))) {
                    int distance = i - j;
                    String hazardType = "RAW";
                    int stallCycles = Math.max(0, 3 - distance);
                    
                    detectedHazards.add(new Hazard(hazardType, i, j, current, prev, stallCycles));
                }
            }
        }
    }
    
    private void setupPipelinedExecution() {
        int numInstructions = instructions.size();
        int maxCycles = numInstructions + 4;
        
        for (Hazard h : detectedHazards) {
            maxCycles += h.stallCycles;
        }
        
        totalCyclesPipeline = maxCycles;
        
        String[] columns = new String[maxCycles + 1];
        columns[0] = "Instruction";
        for (int i = 1; i <= maxCycles; i++) {
            columns[i] = "C" + i;
        }
        
        pipelineModel.setDataVector(new Object[numInstructions][maxCycles + 1], columns);
        
        for (int i = 0; i < numInstructions; i++) {
            pipelineModel.setValueAt(instructions.get(i).toString(), i, 0);
        }
        
        instructionStartCycle = new int[numInstructions];
        for (int i = 0; i < numInstructions; i++) {
            instructionStartCycle[i] = i + 1;
        }
        
        for (Hazard h : detectedHazards) {
            for (int i = h.currentInstrIndex; i < numInstructions; i++) {
                instructionStartCycle[i] += h.stallCycles;
            }
        }
        
        for (int i = 0; i < numInstructions; i++) {
            int startCycle = instructionStartCycle[i];
            for (int stage = 0; stage < stages.length; stage++) {
                int cycle = startCycle + stage;
                if (cycle <= maxCycles) {
                    pipelineModel.setValueAt(stages[stage], i, cycle);
                }
            }
        }
    }
    
    private void setupSequentialExecution() {
        int numInstructions = instructions.size();
        totalCyclesSequential = numInstructions * stages.length;
        
        String[] columns = new String[totalCyclesSequential + 1];
        columns[0] = "Instruction";
        for (int i = 1; i <= totalCyclesSequential; i++) {
            columns[i] = "C" + i;
        }
        
        sequentialModel.setDataVector(new Object[numInstructions][totalCyclesSequential + 1], columns);
        
        for (int i = 0; i < numInstructions; i++) {
            sequentialModel.setValueAt(instructions.get(i).toString(), i, 0);
            
            int startCycle = i * stages.length + 1;
            for (int stage = 0; stage < stages.length; stage++) {
                sequentialModel.setValueAt(stages[stage], i, startCycle + stage);
            }
        }
    }
    
    private void displayHazardDetails() {
        StringBuilder details = new StringBuilder();
        details.append(String.format("Total Hazards Detected: %d\n\n", detectedHazards.size()));
        
        if (detectedHazards.isEmpty()) {
            details.append("No hazards detected! Pipeline runs smoothly.\n");
        } else {
            for (int i = 0; i < detectedHazards.size(); i++) {
                Hazard h = detectedHazards.get(i);
                details.append(String.format("Hazard #%d: %s (Read After Write)\n", i + 1, h.type));
                details.append(String.format("  Between: I%d (%s) and I%d (%s)\n", 
                    h.prevInstrIndex, h.prevInstr.operation, h.currentInstrIndex, h.currentInstr.operation));
                details.append(String.format("  Register: %s written by I%d, read by I%d\n",
                    h.prevInstr.dest, h.prevInstrIndex, h.currentInstrIndex));
                details.append(String.format("  Stall Cycles: %d\n", h.stallCycles));
                details.append(String.format("  Occurs at: Cycle %d\n\n", instructionStartCycle[h.currentInstrIndex] - 1));
            }
        }
        
        hazardDetailsArea.setText(details.toString());
    }
    
    private void updateStatistics() {
        hazardCountLabel.setText(String.format("Hazards Detected: %d", detectedHazards.size()));
        
        double speedup = totalCyclesSequential / (double) totalCyclesPipeline;
        int cycleDifference = totalCyclesSequential - totalCyclesPipeline;
        cycleComparisonLabel.setText(String.format(
            "Pipeline: %d cycles | Sequential: %d cycles | Difference: %d cycles | Speedup: %.2fx",
            totalCyclesPipeline, totalCyclesSequential, cycleDifference, speedup));
    }
    
    class Instruction {
        int index;
        String operation, dest, src1, src2;
        
        Instruction(int index, String op, String dest, String src1, String src2) {
            this.index = index;
            this.operation = op;
            this.dest = dest;
            this.src1 = src1;
            this.src2 = src2;
        }
        
        public String toString() {
            return String.format("I%d: %s %s,%s,%s", index, operation, dest, src1, src2);
        }
    }
    
    class Hazard {
        String type;
        int currentInstrIndex, prevInstrIndex;
        Instruction currentInstr, prevInstr;
        int stallCycles;
        
        Hazard(String type, int curr, int prev, Instruction currInstr, Instruction prevInstr, int stalls) {
            this.type = type;
            this.currentInstrIndex = curr;
            this.prevInstrIndex = prev;
            this.currentInstr = currInstr;
            this.prevInstr = prevInstr;
            this.stallCycles = stalls;
        }
    }
    
    class PipelineRenderer extends DefaultTableCellRenderer {
        List<Hazard> hazards;
        int[] startCycles;
        int currentCycle;
        Color[] stageColors = {
            new Color(200, 230, 255), // IF
            new Color(255, 230, 200), // ID
            new Color(200, 255, 200), // EX
            new Color(255, 255, 200), // MEM
            new Color(230, 200, 255)  // WB
        };
        
        PipelineRenderer(List<Hazard> hazards, int[] startCycles, int currentCycle) {
            this.hazards = hazards;
            this.startCycles = startCycles;
            this.currentCycle = currentCycle;
            setHorizontalAlignment(CENTER);
        }
        
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int col) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
            
            if (col == 0) {
                c.setBackground(Color.LIGHT_GRAY);
                setFont(getFont().deriveFont(Font.BOLD));
            } else if (value != null) {
                String stage = value.toString();
                Color baseColor = Color.WHITE;
                for (int i = 0; i < stages.length; i++) {
                    if (stage.equals(stages[i])) {
                        baseColor = stageColors[i];
                        break;
                    }
                }
                
                // Highlight current cycle
                if (col == currentCycle) {
                    c.setBackground(brighten(baseColor));
                    setBorder(BorderFactory.createLineBorder(Color.RED, 2));
                } else if (col < currentCycle) {
                    c.setBackground(darken(baseColor));
                    setBorder(null);
                } else {
                    c.setBackground(baseColor);
                    setBorder(null);
                }
                setFont(getFont().deriveFont(Font.BOLD));
            } else {
                // Check for stall
                boolean isStall = false;
                for (Hazard h : hazards) {
                    if (row == h.currentInstrIndex && col > 0 && col < startCycles[row]) {
                        isStall = true;
                        break;
                    }
                }
                
                if (isStall) {
                    if (col == currentCycle) {
                        c.setBackground(new Color(255, 100, 100));
                        setBorder(BorderFactory.createLineBorder(Color.RED, 2));
                    } else if (col < currentCycle) {
                        c.setBackground(new Color(200, 150, 150));
                        setBorder(null);
                    } else {
                        c.setBackground(new Color(255, 200, 200));
                        setBorder(null);
                    }
                } else {
                    c.setBackground(Color.WHITE);
                    setBorder(null);
                }
                setFont(getFont().deriveFont(Font.PLAIN));
            }
            
            return c;
        }
        
        private Color brighten(Color color) {
            int r = Math.min(255, color.getRed() + 40);
            int g = Math.min(255, color.getGreen() + 40);
            int b = Math.min(255, color.getBlue() + 40);
            return new Color(r, g, b);
        }
        
        private Color darken(Color color) {
            int r = Math.max(0, color.getRed() - 50);
            int g = Math.max(0, color.getGreen() - 50);
            int b = Math.max(0, color.getBlue() - 50);
            return new Color(r, g, b);
        }
    }
    
    class SequentialRenderer extends DefaultTableCellRenderer {
        int currentCycle;
        Color[] stageColors = {
            new Color(200, 230, 255),
            new Color(255, 230, 200),
            new Color(200, 255, 200),
            new Color(255, 255, 200),
            new Color(230, 200, 255)
        };
        
        SequentialRenderer(int currentCycle) {
            this.currentCycle = currentCycle;
            setHorizontalAlignment(CENTER);
        }
        
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int col) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
            
            if (col == 0) {
                c.setBackground(Color.LIGHT_GRAY);
                setFont(getFont().deriveFont(Font.BOLD));
            } else if (value != null) {
                String stage = value.toString();
                Color baseColor = Color.WHITE;
                for (int i = 0; i < stages.length; i++) {
                    if (stage.equals(stages[i])) {
                        baseColor = stageColors[i];
                        break;
                    }
                }
                
                // Highlight current cycle
                if (col == currentCycle) {
                    c.setBackground(brighten(baseColor));
                    setBorder(BorderFactory.createLineBorder(Color.BLUE, 2));
                } else if (col < currentCycle) {
                    c.setBackground(darken(baseColor));
                    setBorder(null);
                } else {
                    c.setBackground(baseColor);
                    setBorder(null);
                }
                setFont(getFont().deriveFont(Font.BOLD));
            } else {
                c.setBackground(Color.WHITE);
                setBorder(null);
                setFont(getFont().deriveFont(Font.PLAIN));
            }
            
            return c;
        }
        
        private Color brighten(Color color) {
            int r = Math.min(255, color.getRed() + 40);
            int g = Math.min(255, color.getGreen() + 40);
            int b = Math.min(255, color.getBlue() + 40);
            return new Color(r, g, b);
        }
        
        private Color darken(Color color) {
            int r = Math.max(0, color.getRed() - 50);
            int g = Math.max(0, color.getGreen() - 50);
            int b = Math.max(0, color.getBlue() - 50);
            return new Color(r, g, b);
        }
    }
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new PipelineVisualizer());
    }
}