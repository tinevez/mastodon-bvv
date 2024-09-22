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
package org.mastodon.mamut.views.bvv;

import static org.mastodon.mamut.views.bdv.MamutViewBdvFactory.getBdvGuiState;
import static org.mastodon.mamut.views.bdv.MamutViewBdvFactory.restoreBdvGuiState;

import java.util.Map;

import org.mastodon.mamut.ProjectModel;
import org.mastodon.mamut.views.AbstractMamutViewFactory;
import org.mastodon.mamut.views.MamutViewFactory;
import org.mastodon.mamut.views.bdv.MamutViewBdv;
import org.mastodon.mamut.views.bdv.MamutViewBdvFactory;
import org.scijava.Priority;
import org.scijava.plugin.Plugin;

/**
 * Factory to create and display BVV views.
 * <p>
 * The GUI state is specified as a map of strings to objects. The accepted key
 * and value types are the same than for {@link MamutViewBdv}.
 * 
 * @see MamutViewBdvFactory
 */
@Plugin( type = MamutViewFactory.class, priority = Priority.NORMAL )
public class MamutViewBvvFactory extends AbstractMamutViewFactory< MamutViewBvv >
{

	public static final String NEW_BVV_VIEW = "new bvv view";

	static final String[] NEW_BVV_VIEW_KEYS = new String[] { "not mapped" };


	@Override
	public MamutViewBvv create( final ProjectModel projectModel )
	{
		return new MamutViewBvv( projectModel );
	}

	@Override
	public Map< String, Object > getGuiState( final MamutViewBvv view )
	{
		final Map< String, Object > guiState = super.getGuiState( view );
		getBdvGuiState( view.getViewerPanelMamut(), guiState );
		return guiState;
	}

	@Override
	public void restoreGuiState( final MamutViewBvv view, final Map< String, Object > guiState )
	{
		super.restoreGuiState( view, guiState );
		restoreBdvGuiState( view.getViewerPanelMamut(), guiState );
	}

	@Override
	public String getCommandName()
	{
		return NEW_BVV_VIEW;
	}

	@Override
	public String[] getCommandKeys()
	{
		return NEW_BVV_VIEW_KEYS;
	}

	@Override
	public String getCommandDescription()
	{
		return "Open a new BigVolumeViewer view.";
	}

	@Override
	public String getCommandMenuText()
	{
		return "New Bvv";
	}

	@Override
	public Class< MamutViewBvv > getViewClass()
	{
		return MamutViewBvv.class;
	}
}
