/**
 * 
 */
package inra.ijpb.plugins;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Panel;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.gui.ImageCanvas;
import ij.gui.ImageRoi;
import ij.gui.Overlay;
import ij.gui.StackWindow;
import ij.plugin.PlugIn;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;
import ij.process.LUT;
import inra.ijpb.color.CommonColors;

/**
 * Display a label map or a binary image as overlay onto a grayscale image.
 * 
 * @author dlegland
 *
 */
public class ImageOverlayPlugin implements PlugIn
{
    /** main GUI window */
    private CustomWindow win;
    
    /** original input image */
    ImagePlus refImage = null;
    
    /** the image to overlay */
    ImagePlus overlayImage = null;
    
    /**
     * The image to be displayed in the GUI, obtained as a combination of input
     * and label images.
     */
    ImagePlus displayImage = null;
    
    /** flag to indicate 2D input image */
    boolean inputIs2D = false;
    
    /** 
     * The list of images currently open.
     * Determined when plugin run, and used during frame creation.
     */
    String[] imageNames;
    
    
    boolean binaryOverlay = true;
    
    /** The color to use to display binary overlays */ 
    Color overlayColor = Color.RED;
    
    /** opacity to display overlays, between 0 and 100. */
    double overlayOpacity = 33.0;
    
    /** Executor service to launch threads for the plugin methods and events */
    final ExecutorService exec = Executors.newFixedThreadPool(1);

//    /** thread to run the segmentation */
//    private Thread segmentationThread = null;

    
    /**
     * Combines the reference image and the overlay image (and the overlay color
     * if the overlay image is binary) to update the image to display.
     */
    public void updateDisplayImage()
    {
        int sliceIndex = displayImage.getCurrentSlice();
        
        ImageRoi roi = null;
        
        if (overlayImage != null)
        {
            // assume overlay is binary
            if (binaryOverlay)
            {
                ImageProcessor overlayProcessor = overlayImage.getImageStack().getProcessor(sliceIndex);
                overlayProcessor = overlayProcessor.duplicate(); // to avoid side effect on original overlay image
                overlayProcessor.setLut(LUT.createLutFromColor(overlayColor));
                
                roi = new ImageRoi(0, 0, overlayProcessor);
                roi.setZeroTransparent(true);
            }
            else
            {
                // assumes label image -> use original LUT
                System.out.println("use label overlay");
                ImageProcessor overlayProcessor = overlayImage.getImageStack().getProcessor(sliceIndex);
                roi = new ImageRoi(0, 0, overlayProcessor);
                roi.setZeroTransparent(true);
            }
            roi.setOpacity(overlayOpacity * 0.01);
        }
        
        displayImage.setOverlay(new Overlay(roi));
    }
    
    /**
     * Computes the result (RGB) image by combining the reference image with the
     * overlay image, and the other options.
     * 
     * @return a new RGB image.
     */
    public ImagePlus computeResultImage()
    {
        if (refImage.isStack())
        {
            // process 3D
            System.out.println("3D");
            int nSlices = refImage.getStackSize();
            ImageStack resStack = ImageStack.create(refImage.getWidth(), refImage.getHeight(), nSlices, 24);
            for (int z = 1; z <= nSlices; z++)
            {
                IJ.showProgress(z, nSlices);
                ImageProcessor image = refImage.getImageStack().getProcessor(z);
                ImageProcessor overlay = overlayImage.getImageStack().getProcessor(z);
                ImageProcessor result = computeResultImage_2d(image, overlay, overlayColor, (int) overlayOpacity);
                resStack.setProcessor(result, z);
            }
            return new ImagePlus("Overlay", resStack);
        }
        else
        {
            // process 2D
            System.out.println("2D");
            ImageProcessor image = refImage.getProcessor();
            ImageProcessor overlay = overlayImage.getProcessor();
            ImageProcessor result = computeResultImage_2d(image, overlay, overlayColor, (int) overlayOpacity);
            return new ImagePlus("Overlay", result);
        }
    }
    
    private ImageProcessor computeResultImage_2d(ImageProcessor image, ImageProcessor overlay, Color overlayColor, int overlayOpacity)
    {
        // retrieve image size
        int sizeX = image.getWidth();
        int sizeY = image.getHeight();
        
        // pre-compute opacity weights for gray and overlay
        double op0 = overlayOpacity * 0.01;
        double op1 = 1 - op0;
        
        int rOvr = overlayColor.getRed();
        int gOvr = overlayColor.getGreen();
        int bOvr = overlayColor.getBlue();
        
        // the values for result pixel
        int r, g, b;
        
        ColorProcessor result = new ColorProcessor(sizeX, sizeY);
        for (int y = 0; y < sizeY; y++)
        {
            for (int x = 0; x < sizeX; x++)
            {
                int gray = image.get(x, y); // assume 8-bit image
                boolean ovr = overlay.get(x, y) > 0;
                r = ovr ? (int) (gray * op1 + rOvr * op0) : gray;
                g = ovr ? (int) (gray * op1 + gOvr * op0) : gray;
                b = ovr ? (int) (gray * op1 + bOvr * op0) : gray;
                result.set(x, y, r << 16 | g << 8 | b); 
            }
        }
        return result;
    }    
    
    /**
     * The run method used to start the plugin from the GUI.
     * 
     * @param arg
     *            the string argument of the plugin
     */
    @Override
    public void run(String arg)
    {
        if (IJ.getVersion().compareTo("1.48a") < 0)
        {
            IJ.error("Label Map Overlay", "ERROR: detected ImageJ version "
                    + IJ.getVersion()
                    + ".\nLabel Map Overlay requires version 1.48a or superior, please update ImageJ!");
            return;
        }
        
        // get current image, and returns if no image is loaded
        refImage = WindowManager.getCurrentImage();
        if (refImage == null)
        {
            return;
        }
        
        if (refImage.getType() == ImagePlus.COLOR_256
                || refImage.getType() == ImagePlus.COLOR_RGB)
        {
            IJ.error("Label Map Overlay",
                    "This plugin only works on grayscale images.\nPlease convert it to 8, 16 or 32-bit.");
            return;
        }
        
        // initialize image name list
        imageNames = WindowManager.getImageTitles();
        
        // make a copy of the input stack and use it for display
        ImageStack displayStack = refImage.getImageStack();
        displayImage = new ImagePlus(refImage.getTitle(), displayStack);
        displayImage.setTitle("Label Map Overlay");
        displayImage.setSlice(refImage.getCurrentSlice());
        
        // hide input image (to avoid accidental closing)
        refImage.getWindow().setVisible(false);
        
        // set the 2D flag
        inputIs2D = refImage.getImageStackSize() == 1;
        
        // correct Fiji error when the slices are read as frames
        if (inputIs2D == false && displayImage.isHyperStack() == false
                && displayImage.getNSlices() == 1)
        {
            // correct stack by setting number of frames as slices
            displayImage.setDimensions(1, displayImage.getNFrames(), 1);
        }
        
        // Build GUI
        SwingUtilities.invokeLater(new Runnable()
        {
            public void run()
            {
                win = new CustomWindow(displayImage);
                win.pack();
            }
        });
    }
    
    /**
     * Custom window to display together plugin option and result image.
     */
    private class CustomWindow extends StackWindow
    {
        /**
         * Serial version UID
         */
        private static final long serialVersionUID = 1L;
        
        /** parameters panel (segmentation + display options) */
        JPanel paramsPanel = new JPanel();

        /** main panel */
        Panel all = new Panel();
        
        
        // Widgets 
        
        JComboBox<String> overlayImageCombo;
        
        JLabel overlayColorLabel;
        JComboBox<String> overlayColorNameCombo;
        
        JPanel overlayOpacityPanel;
        JTextField overlayOpacityTextField;
        JSlider overlayOpacitySlider;
        
        JButton resultButton = null;    
        
        /**
         * Construct the plugin window.
         * 
         * @param imp
         *            input image
         */
        CustomWindow(ImagePlus imp)
        {
            super(imp, new ImageCanvas(imp));
            
            adjustCurrentZoom();
            
            buildLayout();
            
            setTitle("Image Overlay");
        }
        
        /**
         * Ensure that the current frame has a size compatible for the display
         * of the current image, zooming in or out to make image more visible.
         */
        private void adjustCurrentZoom()
        {
            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            double screenWidth = screenSize.getWidth();
            double screenHeight = screenSize.getHeight();
            
            // Zoom in if image is too small
            while ((ic.getWidth() < screenWidth * 0.25 || ic.getHeight() < screenHeight * 0.25)
                    && ic.getMagnification() < 16.0)
            {
                final int canvasWidth = ic.getWidth();
                ic.zoomIn(0, 0);
                // check if canvas size changed (otherwise stop zooming)
                if (canvasWidth == ic.getWidth())
                {
                    ic.zoomOut(0, 0);
                    break;
                }
            }
            
            // Zoom out if canvas is too large
            while ((ic.getWidth() > screenWidth * 0.75 || ic.getHeight() > screenHeight * 0.75)
                    && ic.getMagnification() > 1 / 32.0)
            {
                final int canvasWidth = ic.getWidth();
                ic.zoomOut(0, 0);
                // check if canvas size changed (otherwise stop zooming)
                if (canvasWidth == ic.getWidth())
                {
                    ic.zoomIn(0, 0);
                    break;
                }
            }
        }
        
        private void buildLayout()
        {
            final ImageCanvas canvas = (ImageCanvas) getCanvas();

            
            // label for drop down list
            JLabel overlayImageLabel = new JLabel( "Overlay Image" );         
            overlayImageLabel.setToolTipText("The binary or label image to overlay");            
            
            // Combo box for choosing image
            overlayImageCombo = new JComboBox<>(imageNames);
            overlayImageCombo.setToolTipText("The binary or label image to overlay");
            overlayImageCombo.addItemListener(new ItemListener() 
            {
                @Override
                public void itemStateChanged(ItemEvent evt)
                {
                    // work only when items are selected (not when they are unselected)
                    if ((evt.getStateChange() & ItemEvent.SELECTED) == 0)
                    {
                        return;
                    }
                    
                    exec.submit(new Runnable() 
                    {
                        public void run()
                        {
                            // start by clearing current overlay image
                            overlayImage = null;
                            
                            // retrieve overlay image 
                            int index = overlayImageCombo.getSelectedIndex();
                            if (index < 0) return;
                            String imageName = imageNames[index];
                            ImagePlus image = WindowManager.getImage(imageName);
                            
                            // check size
                            if (image.getWidth() != refImage.getWidth() || image.getHeight() != refImage.getHeight())
                            {
                                return;
                            }
                            
                            overlayImage = image;
                            
                            updateDisplayImage();
                        }
                    });
                }
            });

            
            // Create layout for input images panel
            JPanel inputImagesPanel = new JPanel();
            inputImagesPanel.setBorder(BorderFactory.createTitledBorder("Input Images"));
            inputImagesPanel.add(overlayImageCombo);
            
            
            // setup widgets for overlay panel
            JCheckBox binaryOverlayCheckbox = new JCheckBox("Binary Overlay", binaryOverlay);
            binaryOverlayCheckbox.addActionListener(new ActionListener() 
            {
                @Override
                public void actionPerformed(ActionEvent e)
                {
                    boolean state = binaryOverlayCheckbox.isSelected();
                    binaryOverlay = state;
                    overlayColorLabel.setEnabled(state);
                    overlayColorNameCombo.setEnabled(state);
                    updateDisplayImage();
                }
            });
            
            overlayColorLabel = new JLabel("Overlay Color:");
            overlayColorNameCombo = new JComboBox<String>(CommonColors.getAllLabels());
            overlayColorNameCombo.setSelectedItem(CommonColors.RED.getLabel());
            overlayColorNameCombo.addItemListener(new ItemListener() 
            {
                @Override
                public void itemStateChanged(ItemEvent evt)
                {
                    // work only when items are selected (not when they are unselected)
                    if ((evt.getStateChange() & ItemEvent.SELECTED) == 0)
                    {
                        return;
                    }
                    
                    exec.submit(new Runnable() 
                    {
                        public void run()
                        {
                            // retrieve overlay color name 
                            String colorName = (String) overlayColorNameCombo.getSelectedItem();
                            
                            // parse color and update display
                            overlayColor = CommonColors.fromLabel(colorName).getColor();
                            
                            updateDisplayImage();
                        }
                    });
                }
            });
            
            // Create widgets for opacity
            JLabel overlayLabel = new JLabel("Opacity");
            overlayLabel.setToolTipText("Overlay opacity, between 0 and 100");
            String opacityText = String.format(Locale.ENGLISH, "%4.1f", overlayOpacity);
            overlayOpacityTextField = new JTextField(opacityText, 5);
            overlayOpacityTextField.setToolTipText("Tolerance in the search of local minima");
            overlayOpacityTextField.addActionListener(new ActionListener() 
            {
                @Override
                public void actionPerformed(ActionEvent e)
                {
                    String text = overlayOpacityTextField.getText();
                    double value;
                    try 
                    {
                        value = Double.parseDouble(text);
                    }
                    catch (Exception ex)
                    {
                        return;
                    }
                    
                    overlayOpacity = Math.min(Math.max(value, 0.0), 100.0);
                    updateOverlayOpacity(overlayOpacity);
                    updateDisplayImage();
                }
            });
            
            overlayOpacitySlider = new JSlider(SwingConstants.HORIZONTAL, 0, 100, (int) overlayOpacity);
            overlayOpacitySlider.setToolTipText("Overlay opacity, between 0 and 100");
            overlayOpacitySlider.addChangeListener(new ChangeListener()
            {
                @Override
                public void stateChanged(ChangeEvent e)
                {
                    int value = overlayOpacitySlider.getValue();
                    overlayOpacity = value;
                    updateOverlayOpacity(value);
                    updateDisplayImage();
                }
            });
            
            
            // Create layout for overlay panel
            JPanel overlayPanel = new JPanel();
            overlayPanel.setBorder(BorderFactory.createTitledBorder("Overlay"));
            GridBagLayout overlayPanelLayout = new GridBagLayout();
            GridBagConstraints overlayPanelConstraints = newConstraints();
            overlayPanel.setLayout(overlayPanelLayout);
            
            overlayPanel.add(binaryOverlayCheckbox, overlayPanelConstraints);
            overlayPanelConstraints.gridy++;

            JPanel overlayColorPanel = new JPanel();
            overlayColorPanel.add(overlayColorLabel);
            overlayColorPanel.add(overlayColorNameCombo);
            overlayPanel.add(overlayColorPanel, overlayPanelConstraints);
            overlayPanelConstraints.gridy++;

            overlayOpacityPanel = new JPanel();
            overlayOpacityPanel.add(overlayLabel);
            overlayOpacityPanel.add(overlayOpacityTextField);
            overlayPanel.add(overlayOpacityPanel, overlayPanelConstraints);
            overlayPanelConstraints.gridy++;
            
            overlayPanel.add(overlayOpacitySlider, overlayPanelConstraints);
            overlayPanelConstraints.gridy++;

            
            resultButton = new JButton("Create Image");
            //          resultButton.setEnabled( false );
            resultButton.setToolTipText( "Show Overlay result in new window" );
            resultButton.addActionListener(new ActionListener()
            {
                @Override
                public void actionPerformed(ActionEvent e)
                {
                    exec.submit(new Runnable() 
                    {
                        public void run()
                        {
                            IJ.log("compute result image");
                            ImagePlus resPlus = computeResultImage();
                            IJ.log("Show overlay image");
                            resPlus.show();
                        }
                    });

                }
            });                 

            // Create layout for results panel
            JPanel resultsPanel = new JPanel();
            resultsPanel.setBorder(BorderFactory.createTitledBorder("Result"));
            GridBagLayout resultsPanelLayout = new GridBagLayout();
            GridBagConstraints resultsPanelConstraints = newConstraints();
            resultsPanel.setLayout(resultsPanelLayout);
            
            resultsPanelConstraints.anchor = GridBagConstraints.CENTER;
            resultsPanelConstraints.fill = GridBagConstraints.BOTH;
            resultsPanel.add(resultButton, resultsPanelConstraints );

            
            // Parameter panel, left side of the GUI.
            // It includes three sub-panels: 
            // * Input Image, 
            // * Overlay,
            // * Result).
            GridBagLayout paramsLayout = new GridBagLayout();
            GridBagConstraints paramsConstraints = newConstraints();
            paramsConstraints.anchor = GridBagConstraints.CENTER;
            paramsConstraints.fill = GridBagConstraints.BOTH;
            paramsPanel.setLayout(paramsLayout);
            paramsPanel.add(inputImagesPanel, paramsConstraints);
            paramsConstraints.gridy++;
            paramsPanel.add(overlayPanel, paramsConstraints);
            paramsConstraints.gridy++;
            paramsPanel.add(resultsPanel, paramsConstraints);
            paramsConstraints.gridy++;


            // main panel (including parameters panel and canvas)
            GridBagLayout layout = new GridBagLayout();
            all.setLayout(layout);

            // put parameter panel in place
            GridBagConstraints allConstraints = newConstraints();
            allConstraints.anchor = GridBagConstraints.NORTH;
            allConstraints.fill = GridBagConstraints.BOTH;
            allConstraints.insets = new Insets(0, 0, 0, 0);
            all.add(paramsPanel, allConstraints);
            
            // put canvas in place
            allConstraints.gridx++;
            allConstraints.weightx = 1;
            allConstraints.weighty = 1;
            all.add(canvas, allConstraints);
            
            allConstraints.gridy++;
            allConstraints.weightx = 0;
            allConstraints.weighty = 0;

            // if the input image is 3d, put the
            // slice selectors in place
            if (super.sliceSelector != null)
            {
                sliceSelector.setValue(refImage.getCurrentSlice());
                displayImage.setSlice(refImage.getCurrentSlice());
                
                all.add(super.sliceSelector, allConstraints);
                
                if (null != super.zSelector)
                    all.add(super.zSelector, allConstraints);
                if (null != super.tSelector)
                    all.add(super.tSelector, allConstraints);
                if (null != super.cSelector)
                    all.add(super.cSelector, allConstraints);
            }
            allConstraints.gridy--;

            // setup the layout for the window (?)
            GridBagLayout wingb = new GridBagLayout();
            GridBagConstraints winc = new GridBagConstraints();
            winc.anchor = GridBagConstraints.NORTHWEST;
            winc.fill = GridBagConstraints.BOTH;
            winc.weightx = 1;
            winc.weighty = 1;
            setLayout(wingb);
            add(all, winc);
            
            // Fix minimum size to the preferred size at this point
            pack();
            setMinimumSize(getPreferredSize());
            
            
            
            // add especial listener if the input image is a stack
            if(sliceSelector != null)
            {
                // add adjustment listener to the scroll bar
                sliceSelector.addAdjustmentListener(new AdjustmentListener() 
                {
                    public void adjustmentValueChanged(final AdjustmentEvent e)
                    {
                        exec.submit(new Runnable()
                        {
                            public void run()
                            {
                                if (e.getSource() == sliceSelector)
                                {
                                    updateDisplayImage();
                                }
                            }
                        });
                    }
                });
                
                // mouse wheel listener to update the rois while scrolling
                addMouseWheelListener(new MouseWheelListener()
                {
                    @Override
                    public void mouseWheelMoved(final MouseWheelEvent e)
                    {
                        exec.submit(new Runnable()
                        {
                            public void run()
                            {
                                updateDisplayImage();
                            }
                        });
                    }
                });
                
                // key listener to repaint the display image and the traces
                // when using the keys to scroll the stack
                KeyListener keyListener = new KeyListener()
                {
                    @Override
                    public void keyTyped(KeyEvent e) {}
                    
                    @Override
                    public void keyReleased(final KeyEvent e)
                    {
                        exec.submit(new Runnable()
                        {
                            public void run()
                            {
                                if (e.getKeyCode() == KeyEvent.VK_LEFT
                                        || e.getKeyCode() == KeyEvent.VK_RIGHT
                                        || e.getKeyCode() == KeyEvent.VK_LESS
                                        || e.getKeyCode() == KeyEvent.VK_GREATER
                                        || e.getKeyCode() == KeyEvent.VK_COMMA
                                        || e.getKeyCode() == KeyEvent.VK_PERIOD)
                                {
                                    updateDisplayImage();
                                }
                            }
                        });
                    }
                    
                    @Override
                    public void keyPressed(KeyEvent e) {}
                };
                // add key listener to the window and the canvas
                addKeyListener(keyListener);
                canvas.addKeyListener(keyListener);
            }       
        }
        
        /**
         * Creates a new set of constraints for GridBagLayout using default settings.
         * 
         * @return a new instance of GridBagConstraints
         */
        private GridBagConstraints newConstraints()
        {
            GridBagConstraints constraints = new GridBagConstraints();
            constraints.anchor = GridBagConstraints.NORTHWEST;
            constraints.fill = GridBagConstraints.HORIZONTAL;
            constraints.gridwidth = 1;
            constraints.gridheight = 1;
            constraints.gridx = 0;
            constraints.gridy = 0;
            constraints.insets = new Insets(5, 5, 6, 6);
            return constraints;
        }
        
        
        public void updateOverlayOpacity(double newValue)
        {
            overlayOpacityTextField.setText(Double.toString(newValue));
            overlayOpacitySlider.setValue((int) newValue);
        }

        /**
         * Override windowClosing to display the input image after closing 
         * the GUI and shut down the executor service
         */
        @Override
        public void windowClosing(WindowEvent e)
        {
            super.windowClosing(e);
            
            if (refImage != null)
            {
                if (displayImage != null)
                {
                    refImage.setSlice(displayImage.getCurrentSlice());
                }
                
                // display input image
                refImage.getWindow().setVisible(true);
            }
            
//            // remove listeners
//            borderButton.removeActionListener(listener);
//            objectButton.removeActionListener(listener);
//            gradientCheckBox.removeActionListener(listener);
//            advancedOptionsCheckBox.removeActionListener(listener);
//            segmentButton.removeActionListener(listener);
//            resultDisplayList.removeActionListener(listener);
//            toggleOverlayCheckBox.removeActionListener(listener);
//            resultButton.removeActionListener(listener);
            
            if (null != displayImage)
            {
                // displayImage.close();
                displayImage = null;
            }
            
            // shut down executor service
            exec.shutdownNow();
        }
        
    }
    
    public static final void main(String... args)
    {
        System.out.println("hello...");
        
//        File file = new File("D:\\images\\testImages\\matlab\\grains.png");
//        File file = new File("D:\\dlegland\\dev\\imagej\\morphoLibJ\\testImages\\grains.png");
        File file = new File("D:\\dlegland\\dev\\imagej\\morphoLibJ\\testImages\\grains\\grains.png");
        ImagePlus image = IJ.openImage(file.getAbsolutePath());
        image.show();
        ImagePlus image2 = IJ.openImage(new File("D:\\dlegland\\dev\\imagej\\morphoLibJ\\testImages\\grains\\grains-WTH-median-bin.png").getAbsolutePath());
        image2.show();
        ImagePlus image3 = IJ.openImage(new File("D:\\dlegland\\dev\\imagej\\morphoLibJ\\testImages\\grains\\grains-WTH-areaOpen-lbl2.png").getAbsolutePath());
        image3.show();
        
        // show grayscale image
        IJ.selectWindow(image.getTitle());
        
        ImageOverlayPlugin plugin = new ImageOverlayPlugin();
        plugin.run("");
    }
}
