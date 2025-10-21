import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

// ============================================================================
// Instruction Class
// ============================================================================
class Instruction {
    enum Type {
        ADD, SUB, LOAD, STORE, BRANCH, NOP
    }
    
    private Type type;
    private String destReg;
    private String src1Reg;
    private String src2Reg;
    private int id;
    
    public Instruction(int id, Type type, String destReg, String src1Reg, String src2Reg) {
        this.id = id;
        this.type = type;
        this.destReg = destReg;
        this.src1Reg = src1Reg;
        this.src2Reg = src2Reg;
    }
    
    public Instruction(int id, Type type) {
        this(id, type, "", "", "");
    }
    
    public Type getType() { return type; }
    public String getDestReg() { return destReg; }
    public String getSrc1Reg() { return src1Reg; }
    public String getSrc2Reg() { return src2Reg; }
    public int getId() { return id; }
    
    public boolean writesRegister() {
        return type == Type.ADD || type == Type.SUB || type == Type.LOAD;
    }
    
    public boolean readsRegister(String reg) {
        if (reg == null || reg.isEmpty()) return false;
        return reg.equals(src1Reg) || reg.equals(src2Reg);
    }
    
    public boolean isBranch() {
        return type == Type.BRANCH;
    }
    
    public boolean usesMemory() {
        return type == Type.LOAD || type == Type.STORE;
    }
    
    @Override
    public String toString() {
        switch (type) {
            case ADD:
                return String.format("ADD %s,%s,%s", destReg, src1Reg, src2Reg);
            case SUB:
                return String.format("SUB %s,%s,%s", destReg, src1Reg, src2Reg);
            case LOAD:
                return String.format("LOAD %s,%s", destReg, src1Reg);
            case STORE:
                return String.format("STORE %s,%s", src1Reg, src2Reg);
            case BRANCH:
                return String.format("BRANCH %s", src1Reg);
            case NOP:
                return "NOP (bubble)";
            default:
                return "UNKNOWN";
        }
    }
}

// ============================================================================
// Pipeline Stage Enum
// ============================================================================
enum PipelineStage {
    FETCH("IF", new Color(100, 181, 246)),
    DECODE("ID", new Color(129, 199, 132)),
    EXECUTE("EX", new Color(255, 183, 77)),
    MEMORY("MEM", new Color(206, 147, 216)),
    WRITEBACK("WB", new Color(240, 98, 146));
    
    private final String shortName;
    private final Color color;
    
    PipelineStage(String shortName, Color color) {
        this.shortName = shortName;
        this.color = color;
    }
    
    public String getShortName() { return shortName; }
    public Color getColor() { return color; }
}

// ============================================================================
// Hazard Type Enum
// ============================================================================
enum HazardType {
    NONE(Color.WHITE, "No Hazard", "⚪"),
    DATA(new Color(255, 100, 100), "Data Hazard (RAW)", "🔴"),
    CONTROL(new Color(255, 165, 0), "Control Hazard", "🟠"),
    STRUCTURAL(new Color(153, 102, 255), "Structural Hazard", "🟣");
    
    private final Color color;
    private final String description;
    private final String icon;
    
    HazardType(Color color, String description, String icon) {
        this.color = color;
        this.description = description;
        this.icon = icon;
    }
    
    public Color getColor() { return color; }
    public String getDescription() { return description; }
    public String getIcon() { return icon; }
}

// ============================================================================
// Hazard Info Class
// ============================================================================
class HazardInfo {
    private HazardType type;
    private int cycle;
    private Instruction instruction1;
    private Instruction instruction2;
    private String details;
    
    public HazardInfo(HazardType type, int cycle, Instruction inst1, Instruction inst2, String details) {
        this.type = type;
        this.cycle = cycle;
        this.instruction1 = inst1;
        this.instruction2 = inst2;
        this.details = details;
    }
    
    public HazardType getType() { return type; }
    public int getCycle() { return cycle; }
    public Instruction getInstruction1() { return instruction1; }
    public Instruction getInstruction2() { return instruction2; }
    public String getDetails() { return details; }
    
    @Override
    public String toString() {
        return String.format("Cycle %d: %s - %s", cycle, type.getDescription(), details);
    }
}

// ============================================================================
// Pipeline Entry Class
// ============================================================================
class PipelineEntry {
    private Instruction instruction;
    private PipelineStage stage;
    private HazardType hazard;
    private boolean isStalled;
    private String hazardDetails;
    
    public PipelineEntry(Instruction instruction, PipelineStage stage) {
        this.instruction = instruction;
        this.stage = stage;
        this.hazard = HazardType.NONE;
        this.isStalled = false;
        this.hazardDetails = "";
    }
    
    public Instruction getInstruction() { return instruction; }
    public PipelineStage getStage() { return stage; }
    public void setStage(PipelineStage stage) { this.stage = stage; }
    public HazardType getHazard() { return hazard; }
    public void setHazard(HazardType hazard) { this.hazard = hazard; }
    public boolean isStalled() { return isStalled; }
    public void setStalled(boolean stalled) { isStalled = stalled; }
    public String getHazardDetails() { return hazardDetails; }
    public void setHazardDetails(String details) { this.hazardDetails = details; }
}

// ============================================================================
// Sequential Entry Class
// ============================================================================
class SequentialEntry {
    private Instruction instruction;
    private PipelineStage stage;
    
    public SequentialEntry(Instruction instruction, PipelineStage stage) {
        this.instruction = instruction;
        this.stage = stage;
    }
    
    public Instruction getInstruction() { return instruction; }
    public PipelineStage getStage() { return stage; }
}

// ============================================================================
// Hazard Detector Class - FIXED
// ============================================================================
class HazardDetector {
    private List<HazardInfo> hazardHistory;
    private Set<String> countedHazards;
    
    public HazardDetector() {
        this.hazardHistory = new ArrayList<>();
        this.countedHazards = new HashSet<>();
    }
    
    // FIXED: Check if newInstruction has data dependency with instructions in pipeline
    public HazardType detectDataHazard(Instruction newInstruction, List<PipelineEntry> currentPipeline, int cycle) {
        if (newInstruction == null || newInstruction.getType() == Instruction.Type.NOP) {
            return HazardType.NONE;
        }
        
        // Check each instruction currently in the pipeline
        for (PipelineEntry entry : currentPipeline) {
            Instruction pipeInst = entry.getInstruction();
            
            if (pipeInst == null || pipeInst.getType() == Instruction.Type.NOP) {
                continue;
            }
            
            // If pipeline instruction writes to a register
            if (pipeInst.writesRegister()) {
                String writeReg = pipeInst.getDestReg();
                
                // And new instruction reads from that register
                if (newInstruction.readsRegister(writeReg)) {
                    // And the write hasn't completed yet
                    if (entry.getStage() != PipelineStage.WRITEBACK) {
                        String details = String.format("%s needs %s from %s (currently in %s)", 
                            newInstruction.toString(), writeReg, pipeInst.toString(), entry.getStage().getShortName());
                        
                        String hazardKey = "DATA_" + newInstruction.getId() + "_" + pipeInst.getId() + "_" + cycle;
                        if (!countedHazards.contains(hazardKey)) {
                            hazardHistory.add(new HazardInfo(HazardType.DATA, cycle, newInstruction, pipeInst, details));
                            countedHazards.add(hazardKey);
                        }
                        
                        return HazardType.DATA;
                    }
                }
            }
        }
        
        return HazardType.NONE;
    }
    
    public HazardType detectControlHazard(List<PipelineEntry> currentPipeline, int cycle) {
        for (PipelineEntry entry : currentPipeline) {
            if (entry.getInstruction() != null && entry.getInstruction().isBranch()) {
                PipelineStage stage = entry.getStage();
                // Branch not resolved until after EXECUTE
                if (stage == PipelineStage.FETCH || stage == PipelineStage.DECODE || 
                    stage == PipelineStage.EXECUTE) {
                    String details = String.format("Branch %s in %s - can't fetch next instruction yet",
                        entry.getInstruction().toString(), stage.getShortName());
                    
                    String hazardKey = "CONTROL_" + entry.getInstruction().getId() + "_" + cycle;
                    if (!countedHazards.contains(hazardKey)) {
                        hazardHistory.add(new HazardInfo(HazardType.CONTROL, cycle, 
                            entry.getInstruction(), null, details));
                        countedHazards.add(hazardKey);
                    }
                    
                    return HazardType.CONTROL;
                }
            }
        }
        
        return HazardType.NONE;
    }
    
    public HazardType detectStructuralHazard(List<PipelineEntry> currentPipeline, int cycle) {
        List<Instruction> memInsts = new ArrayList<>();
        
        for (PipelineEntry entry : currentPipeline) {
            if (entry.getInstruction() != null && 
                entry.getStage() == PipelineStage.MEMORY && 
                entry.getInstruction().usesMemory()) {
                memInsts.add(entry.getInstruction());
            }
        }
        
        if (memInsts.size() > 1) {
            String details = String.format("%d instructions competing for memory: %s and %s",
                memInsts.size(), memInsts.get(0).toString(), memInsts.get(1).toString());
            
            String hazardKey = "STRUCTURAL_" + cycle;
            if (!countedHazards.contains(hazardKey)) {
                hazardHistory.add(new HazardInfo(HazardType.STRUCTURAL, cycle, 
                    memInsts.get(0), memInsts.get(1), details));
                countedHazards.add(hazardKey);
            }
            
            return HazardType.STRUCTURAL;
        }
        
        return HazardType.NONE;
    }
    
    public List<HazardInfo> getHazardHistory() {
        return hazardHistory;
    }
    
    public void clearHistory() {
        hazardHistory.clear();
        countedHazards.clear();
    }
    
    public int countHazardsByType(HazardType type) {
        return (int) hazardHistory.stream()
            .filter(h -> h.getType() == type)
            .count();
    }
}

// ============================================================================
// Sequential Simulator Class
// ============================================================================
class SequentialSimulator {
    private List<Instruction> instructions;
    private List<List<SequentialEntry>> history;
    private int currentCycle;
    
    public SequentialSimulator() {
        this.instructions = new ArrayList<>();
        this.history = new ArrayList<>();
        this.currentCycle = 0;
    }
    
    public void setInstructions(List<Instruction> instructions) {
        this.instructions = new ArrayList<>(instructions);
        reset();
    }
    
    public void reset() {
        history.clear();
        currentCycle = 0;
        history.add(new ArrayList<>());
    }
    
    public void step() {
        List<SequentialEntry> newState = new ArrayList<>();
        
        int instructionIndex = currentCycle / 5;
        int stageIndex = currentCycle % 5;
        
        if (instructionIndex < instructions.size()) {
            Instruction inst = instructions.get(instructionIndex);
            PipelineStage stage = PipelineStage.values()[stageIndex];
            newState.add(new SequentialEntry(inst, stage));
        }
        
        history.add(newState);
        currentCycle++;
    }
    
    public List<List<SequentialEntry>> getHistory() {
        return history;
    }
    
    public int getCurrentCycle() {
        return currentCycle;
    }
    
    public List<Instruction> getInstructions() {
        return instructions;
    }
    
    public int getTotalCycles() {
        return instructions.size() * 5;
    }
    
    public boolean isComplete() {
        return currentCycle >= getTotalCycles();
    }
}

// ============================================================================
// Pipeline Simulator Class - FIXED
// ============================================================================
class PipelineSimulator {
    private List<Instruction> instructions;
    private List<List<PipelineEntry>> history;
    private int currentCycle;
    private HazardDetector hazardDetector;
    private int instructionsFetched;
    
    public PipelineSimulator() {
        this.instructions = new ArrayList<>();
        this.history = new ArrayList<>();
        this.currentCycle = 0;
        this.hazardDetector = new HazardDetector();
        this.instructionsFetched = 0;
    }
    
    public void setInstructions(List<Instruction> instructions) {
        this.instructions = new ArrayList<>(instructions);
        reset();
    }
    
    public void reset() {
        history.clear();
        currentCycle = 0;
        instructionsFetched = 0;
        hazardDetector.clearHistory();
        history.add(new ArrayList<>());
    }
    
    public void step() {
        List<PipelineEntry> newState = new ArrayList<>();
        
        // Advance existing instructions in the pipeline
        if (currentCycle > 0) {
            List<PipelineEntry> prevState = history.get(currentCycle - 1);
            
            for (PipelineEntry entry : prevState) {
                PipelineStage nextStage = getNextStage(entry.getStage());
                
                if (nextStage != null) {
                    PipelineEntry newEntry = new PipelineEntry(entry.getInstruction(), nextStage);
                    newState.add(newEntry);
                }
            }
        }
        
        // Try to fetch new instruction
        if (instructionsFetched < instructions.size()) {
            Instruction newInstruction = instructions.get(instructionsFetched);
            
            // Check for hazards BEFORE adding to pipeline
            HazardType dataHazard = hazardDetector.detectDataHazard(newInstruction, newState, currentCycle);
            HazardType controlHazard = hazardDetector.detectControlHazard(newState, currentCycle);
            HazardType structuralHazard = hazardDetector.detectStructuralHazard(newState, currentCycle);
            
            // Determine if we need to stall
            HazardType hazard = HazardType.NONE;
            if (dataHazard != HazardType.NONE) {
                hazard = dataHazard;
            } else if (controlHazard != HazardType.NONE) {
                hazard = controlHazard;
            } else if (structuralHazard != HazardType.NONE) {
                hazard = structuralHazard;
            }
            
            if (hazard != HazardType.NONE) {
                // Insert bubble/stall - DON'T fetch new instruction
                Instruction bubble = new Instruction(-1, Instruction.Type.NOP);
                PipelineEntry stallEntry = new PipelineEntry(bubble, PipelineStage.FETCH);
                stallEntry.setHazard(hazard);
                stallEntry.setStalled(true);
                stallEntry.setHazardDetails(hazard.getDescription());
                newState.add(stallEntry);
                // Don't increment instructionsFetched - we'll try again next cycle
            } else {
                // No hazard - fetch instruction normally
                PipelineEntry newEntry = new PipelineEntry(newInstruction, PipelineStage.FETCH);
                newState.add(newEntry);
                instructionsFetched++;
            }
        }
        
        // Mark hazards on all instructions in pipeline for visualization
        for (PipelineEntry entry : newState) {
            if (entry.getInstruction() != null && !entry.isStalled()) {
                // Check if this instruction is involved in any hazard
                HazardType dataHazard = hazardDetector.detectDataHazard(entry.getInstruction(), newState, currentCycle);
                if (dataHazard != HazardType.NONE) {
                    entry.setHazard(dataHazard);
                    entry.setHazardDetails(dataHazard.getDescription());
                }
            }
        }
        
        history.add(newState);
        currentCycle++;
    }
    
    private PipelineStage getNextStage(PipelineStage current) {
        switch (current) {
            case FETCH: return PipelineStage.DECODE;
            case DECODE: return PipelineStage.EXECUTE;
            case EXECUTE: return PipelineStage.MEMORY;
            case MEMORY: return PipelineStage.WRITEBACK;
            case WRITEBACK: return null;
            default: return null;
        }
    }
    
    public List<List<PipelineEntry>> getHistory() {
        return history;
    }
    
    public int getCurrentCycle() {
        return currentCycle;
    }
    
    public List<Instruction> getInstructions() {
        return instructions;
    }
    
    public boolean isComplete() {
        if (instructions.isEmpty()) return true;
        int maxCycles = instructions.size() + PipelineStage.values().length + 10; // Extra cycles for hazards
        return currentCycle >= maxCycles || (instructionsFetched >= instructions.size() && 
            (currentCycle == 0 || history.get(history.size() - 1).isEmpty()));
    }
    
    public HazardDetector getHazardDetector() {
        return hazardDetector;
    }
    
    public int getDataHazardCount() { 
        return hazardDetector.countHazardsByType(HazardType.DATA);
    }
    
    public int getControlHazardCount() { 
        return hazardDetector.countHazardsByType(HazardType.CONTROL);
    }
    
    public int getStructuralHazardCount() { 
        return hazardDetector.countHazardsByType(HazardType.STRUCTURAL);
    }
    
    public int getTotalHazardCount() { 
        return hazardDetector.getHazardHistory().size();
    }
}

// ============================================================================
// Hazard Detection Panel
// ============================================================================
class HazardDetectionPanel extends JPanel {
    private PipelineSimulator simulator;
    private JTextArea hazardLogArea;
    private JLabel currentHazardsLabel;
    private JPanel currentHazardsPanel;
    
    public HazardDetectionPanel(PipelineSimulator simulator) {
        this.simulator = simulator;
        setLayout(new BorderLayout(5, 5));
        setBorder(new TitledBorder(new LineBorder(Color.RED, 2), 
            "🔍 Hazard Detection Monitor", 
            TitledBorder.LEFT, 
            TitledBorder.TOP,
            new Font("Arial", Font.BOLD, 14)));
        
        JPanel topPanel = new JPanel(new BorderLayout(5, 5));
        currentHazardsLabel = new JLabel("Current Hazards: None");
        currentHazardsLabel.setFont(new Font("Arial", Font.BOLD, 13));
        topPanel.add(currentHazardsLabel, BorderLayout.NORTH);
        
        currentHazardsPanel = new JPanel();
        currentHazardsPanel.setLayout(new BoxLayout(currentHazardsPanel, BoxLayout.Y_AXIS));
        currentHazardsPanel.setBackground(Color.WHITE);
        JScrollPane currentScroll = new JScrollPane(currentHazardsPanel);
        currentScroll.setPreferredSize(new Dimension(300, 120));
        topPanel.add(currentScroll, BorderLayout.CENTER);
        
        add(topPanel, BorderLayout.NORTH);
        
        JPanel logPanel = new JPanel(new BorderLayout(5, 5));
        logPanel.setBorder(new TitledBorder("Hazard History Log"));
        
        hazardLogArea = new JTextArea(8, 30);
        hazardLogArea.setEditable(false);
        hazardLogArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        hazardLogArea.setBackground(new Color(250, 250, 250));
        JScrollPane logScroll = new JScrollPane(hazardLogArea);
        logPanel.add(logScroll, BorderLayout.CENTER);
        
        add(logPanel, BorderLayout.CENTER);
        
        updateDisplay();
    }
    
    public void updateDisplay() {
        currentHazardsPanel.removeAll();
        
        if (simulator.getCurrentCycle() > 0 && simulator.getCurrentCycle() <= simulator.getHistory().size()) {
            List<PipelineEntry> currentState = simulator.getHistory().get(simulator.getCurrentCycle() - 1);
            Map<HazardType, String> activeHazards = new HashMap<>();
            
            for (PipelineEntry entry : currentState) {
                if (entry.getHazard() != HazardType.NONE) {
                    activeHazards.put(entry.getHazard(), entry.getHazardDetails());
                }
            }
            
            for (Map.Entry<HazardType, String> hazard : activeHazards.entrySet()) {
                JPanel hazardItem = createHazardItem(hazard.getKey(), hazard.getValue());
                currentHazardsPanel.add(hazardItem);
            }
            
            if (activeHazards.isEmpty()) {
                currentHazardsLabel.setText("✅ Current Hazards: None - Pipeline running smoothly");
                currentHazardsLabel.setForeground(new Color(0, 128, 0));
            } else {
                currentHazardsLabel.setText("⚠️ Current Hazards: " + activeHazards.size() + " detected");
                currentHazardsLabel.setForeground(Color.RED);
            }
        } else {
            currentHazardsLabel.setText("Current Hazards: None");
            currentHazardsLabel.setForeground(Color.BLACK);
        }
        
        currentHazardsPanel.revalidate();
        currentHazardsPanel.repaint();
        
        updateHazardLog();
    }
    
    private JPanel createHazardItem(HazardType type, String details) {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBackground(type.getColor());
        panel.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(type.getColor().darker(), 2),
            new EmptyBorder(5, 5, 5, 5)
        ));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
        
        JLabel iconLabel = new JLabel(type.getIcon());
        iconLabel.setFont(new Font("Arial", Font.PLAIN, 24));
        panel.add(iconLabel, BorderLayout.WEST);
        
        JPanel textPanel = new JPanel(new GridLayout(2, 1));
        textPanel.setBackground(type.getColor());
        
        JLabel typeLabel = new JLabel(type.getDescription());
        typeLabel.setFont(new Font("Arial", Font.BOLD, 12));
        textPanel.add(typeLabel);
        
        JLabel detailLabel = new JLabel("<html>" + (details.isEmpty() ? "Active" : details) + "</html>");
        detailLabel.setFont(new Font("Arial", Font.PLAIN, 10));
        textPanel.add(detailLabel);
        
        panel.add(textPanel, BorderLayout.CENTER);
        
        return panel;
    }
    
    private void updateHazardLog() {
        StringBuilder log = new StringBuilder();
        List<HazardInfo> history = simulator.getHazardDetector().getHazardHistory();
        
        if (history.isEmpty()) {
            log.append("No hazards detected yet.\n");
            log.append("Hazards will appear here as they are detected.\n\n");
            log.append("Try these instructions to see hazards:\n");
            log.append("  ADD R1,R2,R3\n");
            log.append("  SUB R4,R1,R5  <- Data Hazard (needs R1)\n");
        } else {
            log.append(String.format("=== Hazard Log (%d total) ===\n\n", history.size()));
            
            for (int i = Math.max(0, history.size() - 20); i < history.size(); i++) {
                HazardInfo info = history.get(i);
                log.append(String.format("[Cycle %d] %s %s\n", 
                    info.getCycle(), 
                    info.getType().getIcon(), 
                    info.getType().getDescription()));
                log.append(String.format("  → %s\n\n", info.getDetails()));
            }
        }
        
        hazardLogArea.setText(log.toString());
        hazardLogArea.setCaretPosition(0);
    }
}

// [Continue with remaining classes - SequentialVisualizerPanel, PipelinedVisualizerPanel, etc.]
// These remain the same as before, so I'll include them below

// ============================================================================
// Sequential Visualizer Panel
// ============================================================================
class SequentialVisualizerPanel extends JPanel {
    private SequentialSimulator simulator;
    private static final int CELL_WIDTH = 100;
    private static final int CELL_HEIGHT = 50;
    private static final int HEADER_WIDTH = 200;
    
    public SequentialVisualizerPanel(SequentialSimulator simulator) {
        this.simulator = simulator;
        setBackground(Color.WHITE);
        setBorder(new TitledBorder("Sequential Execution (Non-Pipelined)"));
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        drawSequentialGrid(g2d);
    }
    
    private void drawSequentialGrid(Graphics2D g2d) {
        List<List<SequentialEntry>> history = simulator.getHistory();
        List<Instruction> instructions = simulator.getInstructions();
        
        if (instructions.isEmpty()) {
            g2d.drawString("No instructions loaded", 50, 50);
            return;
        }
        
        g2d.setFont(new Font("Arial", Font.BOLD, 12));
        g2d.setColor(Color.BLACK);
        g2d.drawString("Instruction", 10, 25);
        
        int maxCycles = history.size();
        for (int cycle = 0; cycle < maxCycles; cycle++) {
            int x = HEADER_WIDTH + cycle * CELL_WIDTH;
            g2d.drawString("C" + cycle, x + 35, 25);
        }
        
        int y = 40;
        for (int i = 0; i < instructions.size(); i++) {
            Instruction inst = instructions.get(i);
            
            g2d.setColor(Color.BLACK);
            g2d.setFont(new Font("Arial", Font.PLAIN, 11));
            String instText = truncateString(inst.toString(), 25);
            g2d.drawString(instText, 10, y + CELL_HEIGHT / 2 + 5);
            
            for (int cycle = 0; cycle < history.size(); cycle++) {
                List<SequentialEntry> state = history.get(cycle);
                SequentialEntry entry = findInstructionInState(inst, state);
                
                if (entry != null) {
                    int x = HEADER_WIDTH + cycle * CELL_WIDTH;
                    drawSequentialCell(g2d, x, y, entry);
                }
            }
            
            y += CELL_HEIGHT + 5;
        }
        
        int preferredWidth = HEADER_WIDTH + maxCycles * CELL_WIDTH + 50;
        int preferredHeight = y + 50;
        setPreferredSize(new Dimension(preferredWidth, preferredHeight));
        revalidate();
    }
    
    private SequentialEntry findInstructionInState(Instruction inst, List<SequentialEntry> state) {
        for (SequentialEntry entry : state) {
            if (entry.getInstruction() == inst) {
                return entry;
            }
        }
        return null;
    }
    
    private void drawSequentialCell(Graphics2D g2d, int x, int y, SequentialEntry entry) {
        Color bgColor = entry.getStage().getColor();
        
        g2d.setColor(bgColor);
        g2d.fillRect(x, y, CELL_WIDTH - 2, CELL_HEIGHT - 2);
        
        g2d.setColor(Color.DARK_GRAY);
        g2d.drawRect(x, y, CELL_WIDTH - 2, CELL_HEIGHT - 2);
        
        g2d.setColor(Color.BLACK);
        g2d.setFont(new Font("Arial", Font.BOLD, 12));
        String stageName = entry.getStage().getShortName();
        FontMetrics fm = g2d.getFontMetrics();
        int textX = x + (CELL_WIDTH - fm.stringWidth(stageName)) / 2;
        int textY = y + CELL_HEIGHT / 2 + 5;
        g2d.drawString(stageName, textX, textY);
    }
    
    private String truncateString(String str, int maxLength) {
        if (str.length() <= maxLength) return str;
        return str.substring(0, maxLength - 3) + "...";
    }
}

// ============================================================================
// Pipelined Visualizer Panel
// ============================================================================
class PipelinedVisualizerPanel extends JPanel {
    private PipelineSimulator simulator;
    private static final int CELL_WIDTH = 100;
    private static final int CELL_HEIGHT = 50;
    private static final int HEADER_WIDTH = 200;
    
    public PipelinedVisualizerPanel(PipelineSimulator simulator) {
        this.simulator = simulator;
        setBackground(Color.WHITE);
        setBorder(new TitledBorder("Pipelined Execution (with Hazard Detection)"));
        
        ToolTipManager.sharedInstance().setInitialDelay(100);
        ToolTipManager.sharedInstance().setDismissDelay(10000);
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        drawPipelineGrid(g2d);
    }
    
    private void drawPipelineGrid(Graphics2D g2d) {
        List<List<PipelineEntry>> history = simulator.getHistory();
        List<Instruction> instructions = simulator.getInstructions();
        
        if (instructions.isEmpty()) {
            g2d.drawString("No instructions loaded", 50, 50);
            return;
        }
        
        g2d.setFont(new Font("Arial", Font.BOLD, 12));
        g2d.setColor(Color.BLACK);
        g2d.drawString("Instruction", 10, 25);
        
        int maxCycles = history.size();
        for (int cycle = 0; cycle < maxCycles; cycle++) {
            int x = HEADER_WIDTH + cycle * CELL_WIDTH;
            g2d.drawString("C" + cycle, x + 35, 25);
        }
        
        int y = 40;
        for (int i = 0; i < instructions.size(); i++) {
            Instruction inst = instructions.get(i);
            
            g2d.setColor(Color.BLACK);
            g2d.setFont(new Font("Arial", Font.PLAIN, 11));
            String instText = truncateString(inst.toString(), 25);
            g2d.drawString(instText, 10, y + CELL_HEIGHT / 2 + 5);
            
            for (int cycle = 0; cycle < history.size(); cycle++) {
                List<PipelineEntry> state = history.get(cycle);
                PipelineEntry entry = findInstructionInState(inst, state);
                
                if (entry != null) {
                    int x = HEADER_WIDTH + cycle * CELL_WIDTH;
                    drawCell(g2d, x, y, entry);
                }
            }
            
            y += CELL_HEIGHT + 5;
        }
        
        int preferredWidth = HEADER_WIDTH + maxCycles * CELL_WIDTH + 50;
        int preferredHeight = y + 50;
        setPreferredSize(new Dimension(preferredWidth, preferredHeight));
        revalidate();
    }
    
    private PipelineEntry findInstructionInState(Instruction inst, List<PipelineEntry> state) {
        for (PipelineEntry entry : state) {
            if (entry.getInstruction() == inst) {
                return entry;
            }
        }
        return null;
    }
    
    private void drawCell(Graphics2D g2d, int x, int y, PipelineEntry entry) {
        Color bgColor;
        boolean hasHazard = entry.getHazard() != HazardType.NONE;
        
        if (entry.isStalled()) {
            bgColor = Color.YELLOW;
        } else if (hasHazard) {
            bgColor = entry.getHazard().getColor();
        } else {
            bgColor = entry.getStage().getColor();
        }
        
        g2d.setColor(bgColor);
        g2d.fillRect(x, y, CELL_WIDTH - 2, CELL_HEIGHT - 2);
        
        if (hasHazard) {
            g2d.setColor(entry.getHazard().getColor().darker().darker());
            g2d.setStroke(new BasicStroke(4));
            g2d.drawRect(x + 1, y + 1, CELL_WIDTH - 4, CELL_HEIGHT - 4);
            g2d.setStroke(new BasicStroke(1));
        } else {
            g2d.setColor(Color.DARK_GRAY);
            g2d.drawRect(x, y, CELL_WIDTH - 2, CELL_HEIGHT - 2);
        }
        
        g2d.setColor(Color.BLACK);
        g2d.setFont(new Font("Arial", Font.BOLD, 12));
        String stageName = entry.isStalled() ? "STALL" : entry.getStage().getShortName();
        FontMetrics fm = g2d.getFontMetrics();
        int textX = x + (CELL_WIDTH - fm.stringWidth(stageName)) / 2;
        int textY = y + CELL_HEIGHT / 2 - 5;
        g2d.drawString(stageName, textX, textY);
        
        if (hasHazard) {
            g2d.setFont(new Font("Arial", Font.PLAIN, 16));
            String icon = entry.getHazard().getIcon();
            int iconX = x + (CELL_WIDTH - g2d.getFontMetrics().stringWidth(icon)) / 2;
            g2d.drawString(icon, iconX, y + CELL_HEIGHT / 2 + 15);
        }
    }
    
    @Override
    public String getToolTipText(MouseEvent event) {
        int x = event.getX();
        int y = event.getY();
        
        List<List<PipelineEntry>> history = simulator.getHistory();
        List<Instruction> instructions = simulator.getInstructions();
        
        int cycle = (x - HEADER_WIDTH) / CELL_WIDTH;
        int instIndex = (y - 40) / (CELL_HEIGHT + 5);
        
        if (cycle >= 0 && cycle < history.size() && 
            instIndex >= 0 && instIndex < instructions.size()) {
            
            Instruction inst = instructions.get(instIndex);
            List<PipelineEntry> state = history.get(cycle);
            PipelineEntry entry = findInstructionInState(inst, state);
            
            if (entry != null && entry.getHazard() != HazardType.NONE) {
                return "<html><b>" + entry.getHazard().getDescription() + "</b><br>" +
                       entry.getHazardDetails() + "</html>";
            }
        }
        
        return null;
    }
    
    private String truncateString(String str, int maxLength) {
        if (str.length() <= maxLength) return str;
        return str.substring(0, maxLength - 3) + "...";
    }
}

// ============================================================================
// Statistics Panel
// ============================================================================
class StatisticsPanel extends JPanel {
    private PipelineSimulator pipelinedSim;
    private SequentialSimulator sequentialSim;
    private JLabel seqCyclesLabel;
    private JLabel pipeCyclesLabel;
    private JLabel speedupLabel;
    private JLabel efficiencyLabel;
    private JLabel savedCyclesLabel;
    private JLabel dataHazardLabel;
    private JLabel controlHazardLabel;
    private JLabel structuralHazardLabel;
    private JLabel totalHazardLabel;
    
    public StatisticsPanel(PipelineSimulator pipelinedSim, SequentialSimulator sequentialSim) {
        this.pipelinedSim = pipelinedSim;
        this.sequentialSim = sequentialSim;
        
        setLayout(new GridLayout(9, 2, 10, 5));
        setBorder(new TitledBorder("Performance & Hazard Statistics"));
        
        seqCyclesLabel = new JLabel("Sequential Cycles: 0");
        pipeCyclesLabel = new JLabel("Pipelined Cycles: 0");
        speedupLabel = new JLabel("Speedup: 0.0x");
        efficiencyLabel = new JLabel("Efficiency: 0.0%");
        savedCyclesLabel = new JLabel("Cycles Saved: 0");
        dataHazardLabel = new JLabel("Data Hazards: 0");
        controlHazardLabel = new JLabel("Control Hazards: 0");
        structuralHazardLabel = new JLabel("Structural Hazards: 0");
        totalHazardLabel = new JLabel("Total Hazards: 0");
        
        Font labelFont = new Font("Arial", Font.PLAIN, 12);
        seqCyclesLabel.setFont(labelFont);
        pipeCyclesLabel.setFont(labelFont);
        speedupLabel.setFont(new Font("Arial", Font.BOLD, 12));
        efficiencyLabel.setFont(labelFont);
        savedCyclesLabel.setFont(new Font("Arial", Font.BOLD, 12));
        dataHazardLabel.setFont(labelFont);
        controlHazardLabel.setFont(labelFont);
        structuralHazardLabel.setFont(labelFont);
        totalHazardLabel.setFont(new Font("Arial", Font.BOLD, 12));
        
        add(new JLabel("Sequential (Non-Pipelined):"));
        add(seqCyclesLabel);
        add(new JLabel("Pipelined:"));
        add(pipeCyclesLabel);
        add(new JLabel("Speedup:"));
        add(speedupLabel);
        add(new JLabel("Efficiency Gain:"));
        add(efficiencyLabel);
        add(new JLabel("Cycles Saved:"));
        add(savedCyclesLabel);
        
        add(new JLabel(""));
        add(new JLabel(""));
        
        add(new JLabel("🔴 Data Hazards:"));
        add(dataHazardLabel);
        add(new JLabel("🟠 Control Hazards:"));
        add(controlHazardLabel);
        add(new JLabel("🟣 Structural Hazards:"));
        add(structuralHazardLabel);
        add(new JLabel("Total Hazards Detected:"));
        add(totalHazardLabel);
        
        updateStatistics();
    }
    
    public void updateStatistics() {
        int seqCycles = sequentialSim.getTotalCycles();
        int pipeCycles = pipelinedSim.getCurrentCycle();
        
        seqCyclesLabel.setText("Sequential Cycles: " + seqCycles);
        pipeCyclesLabel.setText("Pipelined Cycles: " + pipeCycles);
        
        if (pipeCycles > 0 && pipelinedSim.isComplete()) {
            double speedup = (double) seqCycles / pipeCycles;
            double efficiency = ((seqCycles - pipeCycles) / (double) seqCycles) * 100;
            int savedCycles = seqCycles - pipeCycles;
            
            speedupLabel.setText(String.format("Speedup: %.2fx", speedup));
            speedupLabel.setForeground(new Color(0, 128, 0));
            
            efficiencyLabel.setText(String.format("Efficiency: %.1f%%", efficiency));
            savedCyclesLabel.setText("Cycles Saved: " + savedCycles);
            savedCyclesLabel.setForeground(new Color(0, 128, 0));
        } else {
            speedupLabel.setText("Speedup: -");
            speedupLabel.setForeground(Color.BLACK);
            efficiencyLabel.setText("Efficiency: -");
            savedCyclesLabel.setText("Cycles Saved: -");
            savedCyclesLabel.setForeground(Color.BLACK);
        }
        
        int dataCount = pipelinedSim.getDataHazardCount();
        int controlCount = pipelinedSim.getControlHazardCount();
        int structuralCount = pipelinedSim.getStructuralHazardCount();
        int totalCount = pipelinedSim.getTotalHazardCount();
        
        dataHazardLabel.setText("Data Hazards: " + dataCount);
        controlHazardLabel.setText("Control Hazards: " + controlCount);
        structuralHazardLabel.setText("Structural Hazards: " + structuralCount);
        totalHazardLabel.setText("Total Hazards: " + totalCount);
        
        if (totalCount > 0) {
            totalHazardLabel.setForeground(Color.RED);
            dataHazardLabel.setForeground(dataCount > 0 ? Color.RED : Color.BLACK);
            controlHazardLabel.setForeground(controlCount > 0 ? new Color(255, 140, 0) : Color.BLACK);
            structuralHazardLabel.setForeground(structuralCount > 0 ? new Color(153, 50, 204) : Color.BLACK);
        } else {
            totalHazardLabel.setForeground(new Color(0, 128, 0));
            dataHazardLabel.setForeground(Color.BLACK);
            controlHazardLabel.setForeground(Color.BLACK);
            structuralHazardLabel.setForeground(Color.BLACK);
        }
    }
}

// [Continue with ControlPanel, InstructionInputPanel, LegendPanel, and Main classes - keeping them the same]

// ============================================================================
// Control Panel
// ============================================================================
class ControlPanel extends JPanel {
    private JButton startButton;
    private JButton stepButton;
    private JButton resetButton;
    private JButton pauseButton;
    private JLabel cycleLabel;
    private javax.swing.Timer timer;
    private PipelineSimulator pipelinedSim;
    private SequentialSimulator sequentialSim;
    private PipelinedVisualizerPanel pipelinedVis;
    private SequentialVisualizerPanel sequentialVis;
    private StatisticsPanel statsPanel;
    private HazardDetectionPanel hazardPanel;
    
    public ControlPanel(PipelineSimulator pipelinedSim, SequentialSimulator sequentialSim,
                       PipelinedVisualizerPanel pipelinedVis, SequentialVisualizerPanel sequentialVis,
                       StatisticsPanel statsPanel, HazardDetectionPanel hazardPanel) {
        this.pipelinedSim = pipelinedSim;
        this.sequentialSim = sequentialSim;
        this.pipelinedVis = pipelinedVis;
        this.sequentialVis = sequentialVis;
        this.statsPanel = statsPanel;
        this.hazardPanel = hazardPanel;
        
        setLayout(new FlowLayout(FlowLayout.LEFT, 10, 10));
        setBorder(new TitledBorder("Controls"));
        
        stepButton = new JButton("Step");
        startButton = new JButton("Start");
        pauseButton = new JButton("Pause");
        resetButton = new JButton("Reset");
        cycleLabel = new JLabel("Cycle: 0");
        
        pauseButton.setEnabled(false);
        
        stepButton.addActionListener(e -> stepSimulation());
        startButton.addActionListener(e -> startSimulation());
        pauseButton.addActionListener(e -> pauseSimulation());
        resetButton.addActionListener(e -> resetSimulation());
        
        timer = new javax.swing.Timer(1000, e -> {
            if (!pipelinedSim.isComplete() || !sequentialSim.isComplete()) {
                stepSimulation();
            } else {
                pauseSimulation();
            }
        });
        
        add(stepButton);
        add(startButton);
        add(pauseButton);
        add(resetButton);
        add(Box.createHorizontalStrut(20));
        add(cycleLabel);
    }
    
    private void stepSimulation() {
        if (!pipelinedSim.isComplete()) {
            pipelinedSim.step();
        }
        if (!sequentialSim.isComplete()) {
            sequentialSim.step();
        }
        updateDisplay();
    }
    
    private void startSimulation() {
        timer.start();
        startButton.setEnabled(false);
        pauseButton.setEnabled(true);
        stepButton.setEnabled(false);
    }
    
    private void pauseSimulation() {
        timer.stop();
        startButton.setEnabled(true);
        pauseButton.setEnabled(false);
        stepButton.setEnabled(true);
    }
    
    private void resetSimulation() {
        pauseSimulation();
        pipelinedSim.reset();
        sequentialSim.reset();
        updateDisplay();
    }
    
    private void updateDisplay() {
        cycleLabel.setText("Cycle: " + Math.max(pipelinedSim.getCurrentCycle(), 
                                                 sequentialSim.getCurrentCycle()));
        pipelinedVis.repaint();
        sequentialVis.repaint();
        statsPanel.updateStatistics();
        hazardPanel.updateDisplay();
    }
}

// ============================================================================
// Instruction Input Panel
// ============================================================================
class InstructionInputPanel extends JPanel {
    private JTextArea instructionArea;
    private PipelineSimulator pipelinedSim;
    private SequentialSimulator sequentialSim;
    private PipelinedVisualizerPanel pipelinedVis;
    private SequentialVisualizerPanel sequentialVis;
    private StatisticsPanel statsPanel;
    private HazardDetectionPanel hazardPanel;
    
    public InstructionInputPanel(PipelineSimulator pipelinedSim, SequentialSimulator sequentialSim,
                                PipelinedVisualizerPanel pipelinedVis, SequentialVisualizerPanel sequentialVis,
                                StatisticsPanel statsPanel, HazardDetectionPanel hazardPanel) {
        this.pipelinedSim = pipelinedSim;
        this.sequentialSim = sequentialSim;
        this.pipelinedVis = pipelinedVis;
        this.sequentialVis = sequentialVis;
        this.statsPanel = statsPanel;
        this.hazardPanel = hazardPanel;
        
        setLayout(new BorderLayout(5, 5));
        setBorder(new TitledBorder("Instructions"));
        
        instructionArea = new JTextArea(8, 30);
        instructionArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        
        // Default sample with data hazard
        instructionArea.setText(
            "ADD R1,R2,R3\n" +
            "SUB R4,R1,R5\n" +
            "LOAD R6,R2\n" +
            "ADD R7,R6,R3"
        );
        
        JScrollPane scrollPane = new JScrollPane(instructionArea);
        add(scrollPane, BorderLayout.CENTER);
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton loadButton = new JButton("Load Instructions");
        JButton sample1Button = new JButton("Data Hazard Sample");
        JButton sample2Button = new JButton("All Hazards Sample");
        
        loadButton.addActionListener(e -> loadInstructions());
        sample1Button.addActionListener(e -> loadDataHazardSample());
        sample2Button.addActionListener(e -> loadAllHazardsSample());
        
        buttonPanel.add(loadButton);
        buttonPanel.add(sample1Button);
        buttonPanel.add(sample2Button);
        add(buttonPanel, BorderLayout.SOUTH);
    }
    
    private void loadInstructions() {
        String text = instructionArea.getText();
        List<Instruction> instructions = parseInstructions(text);
        
        if (instructions.isEmpty()) {
            JOptionPane.showMessageDialog(this, 
                "No valid instructions found!", 
                "Error", 
                JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        pipelinedSim.setInstructions(instructions);
        sequentialSim.setInstructions(instructions);
        pipelinedVis.repaint();
        sequentialVis.repaint();
        statsPanel.updateStatistics();
        hazardPanel.updateDisplay();
        
        JOptionPane.showMessageDialog(this, 
            "Loaded " + instructions.size() + " instructions\n" +
            "Sequential will take " + (instructions.size() * 5) + " cycles\n" +
            "Click Start/Step to see hazards!", 
            "Success", 
            JOptionPane.INFORMATION_MESSAGE);
    }
    
    private void loadDataHazardSample() {
        instructionArea.setText(
            "ADD R1,R2,R3\n" +
            "SUB R4,R1,R5\n" +
            "LOAD R6,R2\n" +
            "ADD R7,R6,R3"
        );
        loadInstructions();
    }
    
    private void loadAllHazardsSample() {
        instructionArea.setText(
            "ADD R1,R2,R3\n" +
            "SUB R4,R1,R5\n" +
            "LOAD R6,R2\n" +
            "ADD R7,R6,R3\n" +
            "STORE R7,R4\n" +
            "BRANCH R1\n" +
            "ADD R8,R3,R4"
        );
        loadInstructions();
    }
    
    private List<Instruction> parseInstructions(String text) {
        List<Instruction> instructions = new ArrayList<>();
        String[] lines = text.split("\n");
        int id = 0;
        
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            
            String[] parts = line.split("\\s+");
            if (parts.length == 0) continue;
            
            String opcode = parts[0].toUpperCase();
            Instruction inst = null;
            
            try {
                switch (opcode) {
                    case "ADD":
                        if (parts.length >= 2) {
                            String[] regs = parts[1].split(",");
                            String dest = regs.length > 0 ? regs[0] : "";
                            String src1 = regs.length > 1 ? regs[1] : "";
                            String src2 = regs.length > 2 ? regs[2] : "";
                            inst = new Instruction(id++, Instruction.Type.ADD, dest, src1, src2);
                        }
                        break;
                    case "SUB":
                        if (parts.length >= 2) {
                            String[] regs = parts[1].split(",");
                            String dest = regs.length > 0 ? regs[0] : "";
                            String src1 = regs.length > 1 ? regs[1] : "";
                            String src2 = regs.length > 2 ? regs[2] : "";
                            inst = new Instruction(id++, Instruction.Type.SUB, dest, src1, src2);
                        }
                        break;
                    case "LOAD":
                        if (parts.length >= 2) {
                            String[] regs = parts[1].split(",");
                            String dest = regs.length > 0 ? regs[0] : "";
                            String src1 = regs.length > 1 ? regs[1] : "";
                            inst = new Instruction(id++, Instruction.Type.LOAD, dest, src1, "");
                        }
                        break;
                    case "STORE":
                        if (parts.length >= 2) {
                            String[] regs = parts[1].split(",");
                            String src1 = regs.length > 0 ? regs[0] : "";
                            String src2 = regs.length > 1 ? regs[1] : "";
                            inst = new Instruction(id++, Instruction.Type.STORE, "", src1, src2);
                        }
                        break;
                    case "BRANCH":
                        if (parts.length >= 2) {
                            String[] regs = parts[1].split(",");
                            String src1 = regs.length > 0 ? regs[0] : "";
                            inst = new Instruction(id++, Instruction.Type.BRANCH, "", src1, "");
                        }
                        break;
                }
                
                if (inst != null) {
                    instructions.add(inst);
                }
            } catch (Exception e) {
                System.err.println("Error parsing instruction: " + line);
            }
        }
        
        return instructions;
    }
}

// ============================================================================
// Legend Panel
// ============================================================================
class LegendPanel extends JPanel {
    public LegendPanel() {
        setLayout(new GridLayout(0, 2, 10, 5));
        setBorder(new TitledBorder("Legend"));
        
        add(createLegendItem("Fetch (IF)", PipelineStage.FETCH.getColor()));
        add(createLegendItem("Decode (ID)", PipelineStage.DECODE.getColor()));
        add(createLegendItem("Execute (EX)", PipelineStage.EXECUTE.getColor()));
        add(createLegendItem("Memory (MEM)", PipelineStage.MEMORY.getColor()));
        add(createLegendItem("Write Back (WB)", PipelineStage.WRITEBACK.getColor()));
        add(createLegendItem("Stall/Bubble", Color.YELLOW));
        add(createLegendItem("🔴 Data Hazard", HazardType.DATA.getColor()));
        add(createLegendItem("🟠 Control Hazard", HazardType.CONTROL.getColor()));
    }
    
    private JPanel createLegendItem(String text, Color color) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        
        JPanel colorBox = new JPanel();
        colorBox.setBackground(color);
        colorBox.setPreferredSize(new Dimension(20, 20));
        colorBox.setBorder(BorderFactory.createLineBorder(Color.BLACK));
        
        JLabel label = new JLabel(text);
        label.setFont(new Font("Arial", Font.PLAIN, 11));
        
        panel.add(colorBox);
        panel.add(label);
        
        return panel;
    }
}

// ============================================================================
// Main Frame
// ============================================================================
class PipelineVisualizerFrame extends JFrame {
    private PipelineSimulator pipelinedSim;
    private SequentialSimulator sequentialSim;
    private PipelinedVisualizerPanel pipelinedVis;
    private SequentialVisualizerPanel sequentialVis;
    private ControlPanel controlPanel;
    private InstructionInputPanel inputPanel;
    private LegendPanel legendPanel;
    private StatisticsPanel statsPanel;
    private HazardDetectionPanel hazardPanel;
    
    public PipelineVisualizerFrame() {
        setTitle("Instruction Pipeline Visualizer - FIXED Hazard Detection");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));
        
        pipelinedSim = new PipelineSimulator();
        sequentialSim = new SequentialSimulator();
        
        pipelinedVis = new PipelinedVisualizerPanel(pipelinedSim);
        pipelinedVis.setToolTipText("");
        sequentialVis = new SequentialVisualizerPanel(sequentialSim);
        
        statsPanel = new StatisticsPanel(pipelinedSim, sequentialSim);
        hazardPanel = new HazardDetectionPanel(pipelinedSim);
        
        controlPanel = new ControlPanel(pipelinedSim, sequentialSim, pipelinedVis, sequentialVis, statsPanel, hazardPanel);
        inputPanel = new InstructionInputPanel(pipelinedSim, sequentialSim, pipelinedVis, sequentialVis, statsPanel, hazardPanel);
        legendPanel = new LegendPanel();
        
        JScrollPane seqScrollPane = new JScrollPane(sequentialVis);
        seqScrollPane.setPreferredSize(new Dimension(800, 200));
        seqScrollPane.getHorizontalScrollBar().setUnitIncrement(16);
        
        JScrollPane pipeScrollPane = new JScrollPane(pipelinedVis);
        pipeScrollPane.setPreferredSize(new Dimension(800, 200));
        pipeScrollPane.getHorizontalScrollBar().setUnitIncrement(16);
        
        JPanel comparisonPanel = new JPanel(new GridLayout(2, 1, 5, 5));
        comparisonPanel.add(seqScrollPane);
        comparisonPanel.add(pipeScrollPane);
        
        JPanel leftPanel = new JPanel(new BorderLayout(5, 5));
        leftPanel.add(inputPanel, BorderLayout.NORTH);
        leftPanel.add(hazardPanel, BorderLayout.CENTER);
        
        JPanel leftBottomPanel = new JPanel(new GridLayout(2, 1, 5, 5));
        leftBottomPanel.add(legendPanel);
        leftBottomPanel.add(statsPanel);
        leftPanel.add(leftBottomPanel, BorderLayout.SOUTH);
        
        JPanel rightPanel = new JPanel(new BorderLayout(5, 5));
        rightPanel.add(comparisonPanel, BorderLayout.CENTER);
        rightPanel.add(controlPanel, BorderLayout.SOUTH);
        
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        splitPane.setDividerLocation(450);
        
        add(splitPane, BorderLayout.CENTER);
        
        JPanel infoPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        infoPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
        JLabel infoLabel = new JLabel(
            "🔍 FIXED: Hazards now properly detected and counted! Try the sample buttons!");
        infoLabel.setFont(new Font("Arial", Font.BOLD, 13));
        infoLabel.setForeground(new Color(200, 0, 0));
        infoPanel.add(infoLabel);
        add(infoPanel, BorderLayout.NORTH);
        
        pack();
        setSize(1500, 900);
        setLocationRelativeTo(null);
    }
}

// ============================================================================
// Main Class
// ============================================================================
public class PipelineVisualizer {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }
            
            PipelineVisualizerFrame frame = new PipelineVisualizerFrame();
            frame.setVisible(true);
        });
    }
}