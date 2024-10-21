package org.mastodon.views.bvv.scene.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.mastodon.app.ui.AbstractStyleManagerYaml;
import org.mastodon.views.bvv.scene.BvvRenderSettings;
import org.yaml.snakeyaml.Yaml;

public class BvvRenderSettingsManager extends AbstractStyleManagerYaml< BvvRenderSettingsManager, BvvRenderSettings >
{
	private static final String STYLE_FILE = System.getProperty( "user.home" ) + "/.mastodon/bvvrendersettings.yaml";

	/**
	 * A {@code BvvRenderSettings} that has the same properties as the default
	 * BvvRenderSettings. In contrast to defaultStyle this will always refer to
	 * the same object, so a consumers can just use this one BvvRenderSettings
	 * to listen for changes and for painting.
	 */
	private final BvvRenderSettings forwardDefaultStyle;

	private final BvvRenderSettings.UpdateListener updateForwardDefaultListeners;

	public BvvRenderSettingsManager()
	{
		this( true );
	}

	public BvvRenderSettingsManager( final boolean loadStyles )
	{
		forwardDefaultStyle = BvvRenderSettings.defaultStyle().copy();
		updateForwardDefaultListeners = () -> forwardDefaultStyle.set( selectedStyle );
		selectedStyle.updateListeners().add( updateForwardDefaultListeners );
		if ( loadStyles )
			loadStyles();
	}

	@Override
	protected List< BvvRenderSettings > loadBuiltinStyles()
	{
		return Collections.unmodifiableList( new ArrayList<>( BvvRenderSettings.defaults ) );
	}

	@Override
	public synchronized void setSelectedStyle( final BvvRenderSettings renderSettings )
	{
		selectedStyle.updateListeners().remove( updateForwardDefaultListeners );
		selectedStyle = renderSettings;
		forwardDefaultStyle.set( selectedStyle );
		selectedStyle.updateListeners().add( updateForwardDefaultListeners );
	}

	/**
	 * Returns a final {@link BvvRenderSettings} instance that always has the
	 * same properties as the default style.
	 *
	 * @return the {@link BvvRenderSettings} instance.
	 */
	public BvvRenderSettings getForwardDefaultStyle()
	{
		return forwardDefaultStyle;
	}

	public void loadStyles()
	{
		loadStyles( STYLE_FILE );
	}

	@Override
	public void saveStyles()
	{
		saveStyles( STYLE_FILE );
	}

	@Override
	protected Yaml createYaml()
	{
		return BvvRenderSettingsIO.createYaml();
	}
}
