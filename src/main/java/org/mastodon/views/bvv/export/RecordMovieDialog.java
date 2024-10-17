/*-
 * #%L
 * Mastodon
 * %%
 * Copyright (C) 2014 - 2024 Tobias Pietzsch, Jean-Yves Tinevez
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package org.mastodon.views.bvv.export;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

import javax.imageio.ImageIO;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JRadioButton;
import javax.swing.JSeparator;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.mastodon.app.MastodonIcons;
import org.mastodon.ui.coloring.ColorBarOverlay;
import org.mastodon.ui.keymap.KeyConfigContexts;
import org.mastodon.ui.keymap.KeyConfigScopes;
import org.mastodon.ui.util.FileChooser;
import org.mastodon.ui.util.FileChooser.DialogType;
import org.mastodon.ui.util.FileChooser.SelectionMode;
import org.mastodon.views.bvv.VolumeViewerFrameMamut;
import org.scijava.plugin.Plugin;
import org.scijava.prefs.DefaultPrefService;
import org.scijava.ui.behaviour.io.gui.CommandDescriptionProvider;
import org.scijava.ui.behaviour.io.gui.CommandDescriptions;
import org.scijava.ui.behaviour.util.Actions;
import org.scijava.ui.behaviour.util.InputActionBindings;

import bdv.export.ProgressWriter;
import bdv.tools.DelayedPackDialog;
import bdv.tools.ToggleDialogAction;
import bdv.ui.keymap.Keymap;
import bdv.ui.keymap.Keymap.UpdateListener;
import bdv.util.Prefs;
import bdv.viewer.AbstractViewerPanel;
import bdv.viewer.OverlayRenderer;
import bvv.core.VolumeViewerPanel;
import ij.io.LogStream;
import io.humble.video.Codec;
import io.humble.video.Encoder;
import io.humble.video.MediaPacket;
import io.humble.video.MediaPicture;
import io.humble.video.Muxer;
import io.humble.video.MuxerFormat;
import io.humble.video.PixelFormat;
import io.humble.video.Rational;
import io.humble.video.awt.MediaPictureConverter;
import io.humble.video.awt.MediaPictureConverterFactory;

public class RecordMovieDialog extends DelayedPackDialog implements OverlayRenderer
{

	public static final String RECORD_MOVIE_DIALOG = "record movie dialog";

	private static final String[] RECORD_MOVIE_DIALOG_KEYS = { "ctrl R" };

	/*
	 * Command descriptions for all provided commands
	 */
	@Plugin( type = CommandDescriptionProvider.class )
	public static class Descriptions extends CommandDescriptionProvider
	{

		public Descriptions()
		{
			super( KeyConfigScopes.MASTODON, KeyConfigContexts.BIGDATAVIEWER );
		}

		@Override
		public void getCommandDescriptions( final CommandDescriptions descriptions )
		{
			descriptions.add( RECORD_MOVIE_DIALOG, RECORD_MOVIE_DIALOG_KEYS, "Show the record movie dialog." );
		}
	}

	/**
	 * Install the record dialog on the specified BDV window.
	 * 
	 * @param actions
	 *            the actions to register the toggle dialog visibility action.
	 * @param bdv
	 *            the BDV frame to capture.
	 * @param tracksOverlay
	 *            the track overlay displayed on the BDV.
	 * @param colorBarOverlay
	 *            the colorbar overlay displayed on the BDV.
	 * @param keymap
	 *            the keymap of the application. If not <code>null</code>, the
	 *            toggle visibility key bindings will also be registered to the
	 *            dialog window.
	 * @return a runnable that should be executed when the BDV window is closed,
	 *         and that closes this dialog and de-registers its listeners.
	 */
	public static Runnable install(
			final Actions actions,
			final VolumeViewerFrameMamut frame,
			final ColorBarOverlay colorBarOverlay,
			final Keymap keymap )
	{
		final RecordMovieDialog dialog = new RecordMovieDialog(
				frame,
				frame.getViewerPanel(),
				colorBarOverlay );
		dialog.setTitle( "Record movie on " + frame.getTitle() );
		frame.getViewerPanel().getDisplay().overlays().add( dialog );
		actions.namedAction( new MyToggleDialogAction( RECORD_MOVIE_DIALOG, dialog ), RECORD_MOVIE_DIALOG_KEYS );

		// Register some keymaps to the dialog itself.
		if ( keymap != null )
		{
			final InputActionBindings keybindings = new InputActionBindings();
			SwingUtilities.replaceUIActionMap( dialog.rootPane, keybindings.getConcatenatedActionMap() );
			SwingUtilities.replaceUIInputMap( dialog.rootPane, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT,
					keybindings.getConcatenatedInputMap() );

			final Actions dialogActions = new Actions( keymap.getConfig(), KeyConfigContexts.BIGDATAVIEWER );
			dialogActions.install( keybindings, "view" );
			final UpdateListener updateListener = () -> dialogActions.updateKeyConfig( keymap.getConfig() );
			keymap.updateListeners().add( updateListener );
			updateListener.keymapChanged();
			final Runnable onClose = () -> {
				keymap.updateListeners().remove( updateListener );
				dialog.setVisible( false );
				dialog.dispose();
			};

			dialogActions.namedAction( new MyToggleDialogAction( RECORD_MOVIE_DIALOG, dialog ),
					RECORD_MOVIE_DIALOG_KEYS );
			return onClose;
		}

		return () -> {
			dialog.setVisible( false );
			dialog.dispose();
		};
	}

	private static final long serialVersionUID = 1L;

	private static final String EXPORT_TO_MOVIE_KEY = "ExportToMovie";

	private static final String PNG_EXPORT_PATH_KEY = "PNGExportPath";

	private static final String MOVIE_EXPORT_PATH_KEY = "MovieExportPath";

	private static final String FPS_KEY = "FPS";

	private final int maxTimepoint;

	private final JTextField tfPathPNGs;

	private final JSpinner spinnerMinTimepoint;

	private final JSpinner spinnerMaxTimepoint;

	private JTextField tfPathMovie;

	public RecordMovieDialog(
			final Frame owner,
			final VolumeViewerPanel viewer,
			final ColorBarOverlay colorBarOverlay )
	{
		super( owner, "Record BDV movie", false );
		maxTimepoint = ( null == viewer ) ? 10 : viewer.state().getNumTimepoints() - 1;
		final DefaultPrefService prefService = new DefaultPrefService();

		final JPanel boxes = new JPanel();
		boxes.setBorder( new EmptyBorder( 5, 5, 5, 5 ) );
		getContentPane().add( boxes, BorderLayout.CENTER );
		final GridBagLayout gblBoxes = new GridBagLayout();
		gblBoxes.columnWidths = new int[] { 309, 0 };
		gblBoxes.rowHeights = new int[] { 0, 0, 30, 0, 25, 30, 0, 0, 30, 10, 30, 0 };
		gblBoxes.columnWeights = new double[] { 1.0, Double.MIN_VALUE };
		gblBoxes.rowWeights = new double[] { 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, Double.MIN_VALUE };
		boxes.setLayout( gblBoxes );

		final JPanel timepointsPanel = new JPanel();
		final GridBagConstraints gbcTimepointsPanel = new GridBagConstraints();
		gbcTimepointsPanel.fill = GridBagConstraints.BOTH;
		gbcTimepointsPanel.insets = new Insets( 0, 0, 5, 0 );
		gbcTimepointsPanel.gridx = 0;
		gbcTimepointsPanel.gridy = 0;
		boxes.add( timepointsPanel, gbcTimepointsPanel );
		timepointsPanel.setLayout( new BoxLayout( timepointsPanel, BoxLayout.X_AXIS ) );

		timepointsPanel.add( new JLabel( "Record from timepoint" ) );

		final Component horizontalGlue = Box.createHorizontalGlue();
		timepointsPanel.add( horizontalGlue );

		spinnerMinTimepoint = new JSpinner();
		spinnerMinTimepoint.setModel( new SpinnerNumberModel( 0, 0, maxTimepoint, 1 ) );
		timepointsPanel.add( spinnerMinTimepoint );

		timepointsPanel.add( Box.createHorizontalGlue() );
		timepointsPanel.add( new JLabel( "to" ) );

		timepointsPanel.add( Box.createHorizontalStrut( 5 ) );
		spinnerMaxTimepoint = new JSpinner();
		spinnerMaxTimepoint.setModel( new SpinnerNumberModel( maxTimepoint, 0, maxTimepoint, 1 ) );
		timepointsPanel.add( spinnerMaxTimepoint );

		final JPanel widthPanel = new JPanel();
		final GridBagConstraints gbcWidthPanel = new GridBagConstraints();
		gbcWidthPanel.fill = GridBagConstraints.BOTH;
		gbcWidthPanel.insets = new Insets( 0, 0, 5, 0 );
		gbcWidthPanel.gridx = 0;
		gbcWidthPanel.gridy = 1;
		boxes.add( widthPanel, gbcWidthPanel );
		widthPanel.setLayout( new BoxLayout( widthPanel, BoxLayout.X_AXIS ) );

		final JLabel lblTargetSize = new JLabel( "Target Size" );
		widthPanel.add( lblTargetSize );

		final GridBagConstraints gbcSeparator = new GridBagConstraints();
		gbcSeparator.anchor = GridBagConstraints.SOUTH;
		gbcSeparator.fill = GridBagConstraints.HORIZONTAL;
		gbcSeparator.insets = new Insets( 0, 0, 5, 0 );
		gbcSeparator.gridx = 0;
		gbcSeparator.gridy = 2;
		boxes.add( new JSeparator(), gbcSeparator );

		final JRadioButton rdbtnToPNG = new JRadioButton( "Record as a folder of PNGs " );
		final GridBagConstraints gbcRdbtnToPNG = new GridBagConstraints();
		gbcRdbtnToPNG.anchor = GridBagConstraints.WEST;
		gbcRdbtnToPNG.insets = new Insets( 0, 0, 5, 0 );
		gbcRdbtnToPNG.gridx = 0;
		gbcRdbtnToPNG.gridy = 3;
		boxes.add( rdbtnToPNG, gbcRdbtnToPNG );

		final JPanel saveAsPanel = new JPanel();
		final GridBagConstraints gbcSaveAsPanel = new GridBagConstraints();
		gbcSaveAsPanel.fill = GridBagConstraints.BOTH;
		gbcSaveAsPanel.insets = new Insets( 0, 0, 5, 0 );
		gbcSaveAsPanel.gridx = 0;
		gbcSaveAsPanel.gridy = 4;
		boxes.add( saveAsPanel, gbcSaveAsPanel );
		saveAsPanel.setLayout( new BoxLayout( saveAsPanel, BoxLayout.X_AXIS ) );

		saveAsPanel.add( new JLabel( "save to" ) );

		saveAsPanel.add( Box.createHorizontalStrut( 5 ) );
		tfPathPNGs = new JTextField();
		saveAsPanel.add( tfPathPNGs );
		tfPathPNGs.setColumns( 20 );

		saveAsPanel.add( Box.createHorizontalStrut( 10 ) );

		final JButton btnBrowsePNGs = new JButton( "Browse" );
		saveAsPanel.add( btnBrowsePNGs );

		final GridBagConstraints gbcSeparator1 = new GridBagConstraints();
		gbcSeparator1.anchor = GridBagConstraints.SOUTH;
		gbcSeparator1.fill = GridBagConstraints.HORIZONTAL;
		gbcSeparator1.insets = new Insets( 0, 0, 5, 0 );
		gbcSeparator1.gridx = 0;
		gbcSeparator1.gridy = 5;
		boxes.add( new JSeparator(), gbcSeparator1 );

		final JRadioButton rdbtnToMovie = new JRadioButton( "Record to a movie file" );
		final GridBagConstraints gbcRdbtnToMovie = new GridBagConstraints();
		gbcRdbtnToMovie.anchor = GridBagConstraints.WEST;
		gbcRdbtnToMovie.insets = new Insets( 0, 0, 5, 0 );
		gbcRdbtnToMovie.gridx = 0;
		gbcRdbtnToMovie.gridy = 6;
		boxes.add( rdbtnToMovie, gbcRdbtnToMovie );

		final JPanel panelSaveTo2 = new JPanel();
		final GridBagConstraints gbcPanelSaveTo2 = new GridBagConstraints();
		gbcPanelSaveTo2.insets = new Insets( 0, 0, 5, 0 );
		gbcPanelSaveTo2.fill = GridBagConstraints.BOTH;
		gbcPanelSaveTo2.gridx = 0;
		gbcPanelSaveTo2.gridy = 7;
		boxes.add( panelSaveTo2, gbcPanelSaveTo2 );
		panelSaveTo2.setLayout( new BoxLayout( panelSaveTo2, BoxLayout.X_AXIS ) );

		final JLabel lblSaveTo2 = new JLabel( "save to" );
		panelSaveTo2.add( lblSaveTo2 );

		panelSaveTo2.add( Box.createHorizontalStrut( 5 ) );

		tfPathMovie = new JTextField();
		panelSaveTo2.add( tfPathMovie );
		tfPathMovie.setColumns( 10 );

		panelSaveTo2.add( Box.createHorizontalStrut( 10 ) );

		final JButton btnBrowseMovie = new JButton( "Browse" );
		panelSaveTo2.add( btnBrowseMovie );

		final JPanel panelFPS = new JPanel();
		final FlowLayout flowLayout = ( FlowLayout ) panelFPS.getLayout();
		flowLayout.setAlignment( FlowLayout.RIGHT );
		final GridBagConstraints gbcPanelFPS = new GridBagConstraints();
		gbcPanelFPS.insets = new Insets( 0, 0, 5, 0 );
		gbcPanelFPS.fill = GridBagConstraints.BOTH;
		gbcPanelFPS.gridx = 0;
		gbcPanelFPS.gridy = 8;
		boxes.add( panelFPS, gbcPanelFPS );

		panelFPS.add( new JLabel( "fps" ) );
		panelFPS.add( Box.createHorizontalStrut( 10 ) );

		final JSpinner spinnerFPS = new JSpinner();
		spinnerFPS.setModel( new SpinnerNumberModel( 10, 1, 200, 1 ) );
		panelFPS.add( spinnerFPS );

		final GridBagConstraints gbcSeparator2 = new GridBagConstraints();
		gbcSeparator2.anchor = GridBagConstraints.NORTH;
		gbcSeparator2.fill = GridBagConstraints.HORIZONTAL;
		gbcSeparator2.insets = new Insets( 0, 0, 5, 0 );
		gbcSeparator2.gridx = 0;
		gbcSeparator2.gridy = 9;
		boxes.add( new JSeparator(), gbcSeparator2 );

		final JPanel panelRecord = new JPanel();
		final GridBagConstraints gbcPanelRecord = new GridBagConstraints();
		gbcPanelRecord.anchor = GridBagConstraints.SOUTH;
		gbcPanelRecord.fill = GridBagConstraints.HORIZONTAL;
		gbcPanelRecord.gridx = 0;
		gbcPanelRecord.gridy = 10;
		boxes.add( panelRecord, gbcPanelRecord );
		panelRecord.setLayout( new BoxLayout( panelRecord, BoxLayout.X_AXIS ) );

		final JProgressBar progressBar = new JProgressBar();
		panelRecord.add( progressBar );

		final JButton recordButton = new JButton( "Record" );
		panelRecord.add( recordButton );

		/*
		 * Progress bar.
		 */

		progressBar.getModel().setMinimum( 0 );
		progressBar.getModel().setMaximum( 100 );
		progressBar.setStringPainted( true );
		final ProgressWriter progressWriter = new ProgressWriter()
		{
			private final LogStream ls = new LogStream();

			@Override
			public void setProgress( final double completionRatio )
			{
				progressBar.setValue( ( int ) ( 100 * completionRatio ) );
			}

			@Override
			public PrintStream out()
			{
				return ls;
			}

			@Override
			public PrintStream err()
			{
				return ls;
			}
		};

		/*
		 * Listeners.
		 */

		spinnerMinTimepoint.addChangeListener( new ChangeListener()
		{
			@Override
			public void stateChanged( final ChangeEvent e )
			{
				final int min = ( Integer ) spinnerMinTimepoint.getValue();
				final int max = ( Integer ) spinnerMaxTimepoint.getValue();
				if ( max < min )
					spinnerMaxTimepoint.setValue( min );
			}
		} );

		spinnerMaxTimepoint.addChangeListener( new ChangeListener()
		{
			@Override
			public void stateChanged( final ChangeEvent e )
			{
				final int min = ( Integer ) spinnerMinTimepoint.getValue();
				final int max = ( Integer ) spinnerMaxTimepoint.getValue();
				if ( min > max )
					spinnerMinTimepoint.setValue( max );
			}
		} );

		btnBrowsePNGs.addActionListener( e -> {
			final File file = FileChooser.chooseFile(
					FileChooser.useJFileChooser,
					RecordMovieDialog.this,
					tfPathPNGs.getText(),
					null,
					"Browse to a folder to save the PNGs to",
					DialogType.SAVE,
					SelectionMode.DIRECTORIES_ONLY,
					MastodonIcons.BDV_ICON_MEDIUM.getImage() );
			if ( file != null )
			{
				tfPathPNGs.setText( file.getAbsolutePath() );
				prefService.put( RecordMovieDialog.class, PNG_EXPORT_PATH_KEY, file.getAbsolutePath() );
			}
		} );

		btnBrowseMovie.addActionListener( e -> {
			final File file = FileChooser.chooseFile(
					FileChooser.useJFileChooser,
					RecordMovieDialog.this,
					tfPathMovie.getText(),
					null,
					"Save to movie file (MP4, MOV, AVI, ...)",
					DialogType.SAVE,
					SelectionMode.FILES_ONLY,
					MastodonIcons.BDV_ICON_MEDIUM.getImage() );
			if ( file != null )
			{
				tfPathMovie.setText( file.getAbsolutePath() );
				prefService.put( RecordMovieDialog.class, MOVIE_EXPORT_PATH_KEY, file.getAbsolutePath() );
			}
		} );

		final ButtonGroup buttonGroup = new ButtonGroup();
		buttonGroup.add( rdbtnToPNG );
		buttonGroup.add( rdbtnToMovie );

		final ItemListener enableGroupListener = e -> {
			if ( e != null && e.getStateChange() == ItemEvent.DESELECTED )
				return;
			final boolean pngEnabled = rdbtnToPNG.isSelected();
			tfPathPNGs.setEnabled( pngEnabled );
			btnBrowsePNGs.setEnabled( pngEnabled );
			tfPathMovie.setEnabled( !pngEnabled );
			btnBrowseMovie.setEnabled( !pngEnabled );
			spinnerFPS.setEnabled( !pngEnabled );

			prefService.put( RecordMovieDialog.class, EXPORT_TO_MOVIE_KEY, !pngEnabled );
		};
		rdbtnToPNG.addItemListener( enableGroupListener );
		rdbtnToMovie.addItemListener( enableGroupListener );

		/*
		 * Persistence.
		 */

		rdbtnToMovie.setSelected( prefService.getBoolean( RecordMovieDialog.class, EXPORT_TO_MOVIE_KEY, true ) );
		tfPathPNGs.setText(
				prefService.get( RecordMovieDialog.class, PNG_EXPORT_PATH_KEY, System.getProperty( "user.home" ) ) );
		tfPathMovie.setText( prefService.get( RecordMovieDialog.class, MOVIE_EXPORT_PATH_KEY,
				new File( System.getProperty( "user.home" ), "BDVCapture.mp4" ).getAbsolutePath() ) );
		int fps = prefService.getInt( RecordMovieDialog.class, FPS_KEY, 10 );
		fps = Math.min( 200, Math.max( 1, fps ) );
		spinnerFPS.setValue( fps );
		spinnerFPS.addChangeListener( e -> prefService.put( RecordMovieDialog.class, FPS_KEY,
				( ( Number ) spinnerFPS.getValue() ).intValue() ) );
		setCanvasSize( viewer.getWidth(), viewer.getHeight() );

		/*
		 * Record action.
		 */

		recordButton.addActionListener( new ActionListener()
		{
			@Override
			public void actionPerformed( final ActionEvent e )
			{
				final boolean toPNG = rdbtnToPNG.isSelected();

				final AbstractBVVRecorder recorder;
				if ( toPNG )
				{
					final String dirname = tfPathPNGs.getText();
					final File dir = new File( dirname );
					if ( !dir.exists() )
						dir.mkdirs();
					if ( !dir.exists() || !dir.isDirectory() )
					{
						progressWriter.err().append( "Invalid export directory " + dirname + '\n' );
						return;
					}
					recorder = new PNGFolderBDVRecorder( viewer, progressWriter, dir );
				}
				else
				{
					final String filename = tfPathMovie.getText();
					final int fps = ( ( Number ) spinnerFPS.getValue() ).intValue();
					recorder = new MovieFileBDVRecorder( viewer, progressWriter, filename, fps );
				}

				final int minTimepointIndex = ( Integer ) spinnerMinTimepoint.getValue();
				final int maxTimepointIndex = ( Integer ) spinnerMaxTimepoint.getValue();
				new Thread()
				{
					@Override
					public void run()
					{
						try
						{
							recordButton.setEnabled( false );
							recorder.record( minTimepointIndex, maxTimepointIndex );
						}
						catch ( final Exception ex )
						{
							ex.printStackTrace();
						}
						finally
						{
							recordButton.setEnabled( true );
						}
					}
				}.start();
			}
		} );

		// Pack.
		pack();
	}

	@Override
	public void drawOverlays( final Graphics g )
	{}

	@Override
	public void setCanvasSize( final int width, final int height )
	{}

	static final class MyToggleDialogAction extends ToggleDialogAction
	{

		private static final long serialVersionUID = 1L;

		public MyToggleDialogAction( final String name, final JDialog dialog )
		{
			super( name, dialog );
		}

		@Override
		public void actionPerformed( final ActionEvent e )
		{
			dialog.setLocationRelativeTo( ( Component ) e.getSource() );
			super.actionPerformed( e );
		}
	}

	public static abstract class AbstractBVVRecorder
	{

		private final AbstractViewerPanel viewer;

		private final BufferedImage bi;

		private final ProgressWriter progressWriter;

		public AbstractBVVRecorder( final AbstractViewerPanel viewer, final ProgressWriter progressWriter )
		{
			this.viewer = viewer;
			this.progressWriter = progressWriter;
			final Component component = viewer.getDisplayComponent();
			final Rectangle rect = component.getBounds();
			this.bi = new BufferedImage( rect.width, rect.height, BufferedImage.TYPE_INT_ARGB );
		}

		public void record( final int minTimepointIndex, final int maxTimepointIndex )
		{
			final boolean[] previousPrefs = storeAndUpdatePrefs();
			final int w = viewer.getDisplayComponent().getWidth();
			final int h = viewer.getDisplayComponent().getHeight();
			initializeRecorder( w, h );

			// Loop over time.
			progressWriter.setProgress( 0 );
			for ( int t = minTimepointIndex; t <= maxTimepointIndex; ++t )
			{
				writeFrame( grab( t ), t );
				progressWriter.setProgress(
						( double ) ( t - minTimepointIndex + 1 ) / ( maxTimepointIndex - minTimepointIndex + 1 ) );
			}

			closeRecorder();
			restorePrefs( previousPrefs );
		}

		protected BufferedImage grab( final int t )
		{
			viewer.state().setCurrentTimepoint( t );
			final Graphics2D g2 = bi.createGraphics();
			g2.setRenderingHint( RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON );
			viewer.getDisplayComponent().paint( g2 );
			return bi;
		}

		protected abstract void writeFrame( BufferedImage frame, int timepoint );

		protected abstract void closeRecorder();

		protected abstract void initializeRecorder( int w, int h );
	}

	public static class MovieFileBDVRecorder extends AbstractBVVRecorder
	{

		private final String filename;

		private final int fps;

		private Muxer muxer;

		private MediaPicture picture;

		private MediaPictureConverter converter;

		private Encoder encoder;

		private MediaPacket packet;

		public MovieFileBDVRecorder( final AbstractViewerPanel viewer, final ProgressWriter progressWriter, final String filename, final int fps )
		{
			super( viewer, progressWriter );
			this.filename = filename;
			this.fps = fps;
		}

		@Override
		protected void writeFrame( final BufferedImage frame, final int timepoint )
		{
			// Convert BI type to something Humble can harness.
			// Also crop in case we had non-even dimensions.
			final BufferedImage convertedImg =
					new BufferedImage( picture.getWidth(), picture.getHeight(), BufferedImage.TYPE_3BYTE_BGR );
			convertedImg.getGraphics().drawImage( frame, 0, 0, null );

			if ( converter == null )
				converter = MediaPictureConverterFactory.createConverter( convertedImg, picture );

			converter.toPicture( picture, convertedImg, timepoint );

			// Write to output video stream.
			do
			{
				encoder.encode( packet, picture );
				if ( packet.isComplete() )
					muxer.write( packet, false );
			}
			while ( packet.isComplete() );
		}

		@Override
		protected void closeRecorder()
		{
			// Flush.
			do
			{
				encoder.encode( packet, null );
				if ( packet.isComplete() )
					muxer.write( packet, false );
			}
			while ( packet.isComplete() );

			// Close.
			muxer.close();

			// Nullify everything.
			converter = null;
			encoder = null;
			muxer = null;
			packet = null;
			picture = null;
		}

		@Override
		protected void initializeRecorder( final int w, final int h )
		{
			/*
			 * Make sure we only get even numbers for width and height.
			 */
			final int width = Math.round( w / 2 ) * 2;
			final int height = Math.round( h / 2 ) * 2;

			/*
			 * First we create a muxer using the filename to determine code and
			 * format.
			 */
			final Rational framerate = Rational.make( 1, fps );
			this.muxer = Muxer.make( filename, null, null );

			// Determine codec.
			final MuxerFormat format = muxer.getFormat();
			final Codec codec = Codec.findEncodingCodec( format.getDefaultVideoCodecId() );

			// Create an encoder.
			encoder = Encoder.make( codec );
			encoder.setWidth( width );
			encoder.setHeight( height );
			// Default pixel format, assuming it's the most used by codecs.
			final PixelFormat.Type pixelformat = PixelFormat.Type.PIX_FMT_YUV420P;
			encoder.setPixelFormat( pixelformat );
			encoder.setTimeBase( framerate );

			// Global headers if needed.
			if ( format.getFlag( MuxerFormat.Flag.GLOBAL_HEADER ) )
				encoder.setFlag( Encoder.Flag.FLAG_GLOBAL_HEADER, true );

			// Open the encoder.
			encoder.open( null, null );

			// Add this stream to the muxer.
			muxer.addNewStream( encoder );

			// And open the muxer for business.
			try
			{
				muxer.open( null, null );
			}
			catch ( InterruptedException | IOException e )
			{
				e.printStackTrace();
			}

			this.picture = MediaPicture.make(
					encoder.getWidth(),
					encoder.getHeight(),
					pixelformat );
			picture.setTimeBase( framerate );

			packet = MediaPacket.make();

		}

	}

	public static class PNGFolderBDVRecorder extends AbstractBVVRecorder
	{

		private final File targetFolder;

		public PNGFolderBDVRecorder(
				final AbstractViewerPanel viewer,
				final ProgressWriter progressWriter,
				final File targetFolder )
		{
			super( viewer, progressWriter );
			this.targetFolder = targetFolder;
		}

		@Override
		protected void writeFrame( final BufferedImage bi, final int t )
		{
			try
			{
				ImageIO.write( bi, "png", new File( String.format( "%s/img-%03d.png", targetFolder, t ) ) );
			}
			catch ( final IOException e )
			{
				e.printStackTrace();
			}
		}

		@Override
		protected void closeRecorder()
		{}

		@Override
		protected void initializeRecorder( final int w, final int h )
		{}
	}

	private static void restorePrefs( final boolean[] previousPrefValues )
	{
		Prefs.showMultibox( previousPrefValues[ 0 ] );
		Prefs.showScaleBar( previousPrefValues[ 1 ] );
		Prefs.showTextOverlay( previousPrefValues[ 2 ] );

	}

	private static boolean[] storeAndUpdatePrefs()
	{
		final boolean showMultibox = Prefs.showMultibox();
		final boolean showScaleBar = Prefs.showScaleBar();
		final boolean showTextOverlay = Prefs.showTextOverlay();

		Prefs.showMultibox( false );
		Prefs.showScaleBar( false );
		Prefs.showTextOverlay( false );

		return new boolean[] { showMultibox, showScaleBar, showTextOverlay };
	}
}
