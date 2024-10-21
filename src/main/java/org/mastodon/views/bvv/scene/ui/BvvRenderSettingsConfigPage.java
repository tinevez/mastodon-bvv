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
package org.mastodon.views.bvv.scene.ui;

import java.awt.BorderLayout;
import java.awt.Frame;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.WindowConstants;

import org.mastodon.views.bvv.scene.BvvRenderSettings;
import org.scijava.listeners.Listeners;

import bdv.ui.settings.ModificationListener;
import bdv.ui.settings.SelectAndEditProfileSettingsPage;
import bdv.ui.settings.SettingsPanel;
import bdv.ui.settings.style.StyleProfile;
import bdv.ui.settings.style.StyleProfileManager;

public class BvvRenderSettingsConfigPage extends SelectAndEditProfileSettingsPage< StyleProfile< BvvRenderSettings > >
{
	/**
	 * Creates a new render-settings config page.
	 *
	 * @param treePath
	 * 		path of this page in the settings tree.
	 * @param renderSettingsManager
	 *            the render settings manager.
	 */
	public BvvRenderSettingsConfigPage( final String treePath, final BvvRenderSettingsManager renderSettingsManager )
	{
		super(
				treePath,
				new StyleProfileManager<>( renderSettingsManager, new BvvRenderSettingsManager( false ) ),
				new BvvRenderSettingsProfileEditPanel( renderSettingsManager.getSelectedStyle() ) );
	}

	static class BvvRenderSettingsProfileEditPanel implements BvvRenderSettings.UpdateListener,
			SelectAndEditProfileSettingsPage.ProfileEditPanel< StyleProfile< BvvRenderSettings > >
	{
		private final Listeners.SynchronizedList< ModificationListener > modificationListeners;

		private final BvvRenderSettings editedStyle;

		private final JPanel styleEditorPanel;

		public BvvRenderSettingsProfileEditPanel( final BvvRenderSettings initialStyle )
		{
			editedStyle = initialStyle.copy( "Edited" );
			styleEditorPanel = new JPanel();
			styleEditorPanel.setLayout( new BorderLayout() );
			styleEditorPanel.add( new BvvRenderSettingsPanel( editedStyle ), BorderLayout.CENTER );

			modificationListeners = new Listeners.SynchronizedList<>();
			editedStyle.updateListeners().add( this );
		}

		private boolean trackModifications = true;

		@Override
		public void bvvRenderSettingsChanged()
		{
			if ( trackModifications )
				modificationListeners.list.forEach( ModificationListener::setModified );
		}

		@Override
		public void loadProfile( final StyleProfile< BvvRenderSettings > profile )
		{
			trackModifications = false;
			editedStyle.set( profile.getStyle() );
			trackModifications = true;
		}

		@Override
		public void storeProfile( final StyleProfile< BvvRenderSettings > profile )
		{
			trackModifications = false;
			editedStyle.setName( profile.getStyle().getName() );
			trackModifications = true;
			profile.getStyle().set( editedStyle );
		}

		@Override
		public Listeners< ModificationListener > modificationListeners()
		{
			return modificationListeners;
		}

		@Override
		public JPanel getJPanel()
		{
			return styleEditorPanel;
		}
	}

	public static void main( final String[] args )
	{
		final BvvRenderSettingsManager styleManager = new BvvRenderSettingsManager();

		final SettingsPanel settings = new SettingsPanel();
		settings.addPage( new BvvRenderSettingsConfigPage( "Style > BVV", styleManager ) );

		final JDialog dialog = new JDialog( ( Frame ) null, "Settings" );
		dialog.getContentPane().add( settings, BorderLayout.CENTER );

		settings.onOk( () -> dialog.setVisible( false ) );
		settings.onCancel( () -> dialog.setVisible( false ) );

		dialog.setDefaultCloseOperation( WindowConstants.DO_NOTHING_ON_CLOSE );
		dialog.addWindowListener( new WindowAdapter()
		{
			@Override
			public void windowClosing( final WindowEvent e )
			{
				settings.cancel();
			}
		} );

		dialog.pack();
		dialog.setVisible( true );
	}
}
