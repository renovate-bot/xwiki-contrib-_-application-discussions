/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.contrib.discussions;

import java.util.Date;
import java.util.List;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xwiki.bridge.event.DocumentCreatedEvent;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.contrib.discussions.domain.Discussion;
import org.xwiki.contrib.discussions.domain.references.DiscussionReference;
import org.xwiki.contrib.discussions.script.DiscussionRightsScriptService;
import org.xwiki.contrib.discussions.script.DiscussionsScriptService;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.observation.EventListener;
import org.xwiki.rendering.RenderingScriptServiceComponentList;
import org.xwiki.rendering.configuration.ExtendedRenderingConfiguration;
import org.xwiki.rendering.internal.macro.wikibridge.DefaultWikiMacroManager;
import org.xwiki.rendering.internal.macro.wikibridge.WikiMacroEventListener;
import org.xwiki.rendering.syntax.Syntax;
import org.xwiki.rendering.wikimacro.internal.DefaultWikiMacro;
import org.xwiki.rendering.wikimacro.internal.DefaultWikiMacroFactory;
import org.xwiki.rendering.wikimacro.internal.DefaultWikiMacroRenderer;
import org.xwiki.script.service.ScriptService;
import org.xwiki.security.authorization.Right;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.junit5.mockito.MockComponent;
import org.xwiki.test.page.HTML50ComponentList;
import org.xwiki.test.page.PageTest;
import org.xwiki.test.page.XWikiSyntax21ComponentList;

import com.xpn.xwiki.doc.XWikiDocument;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Page test of {@code Discussions.Code.DiscussionMacro}.
 *
 * @version $Id$
 */
@ComponentList({
    WikiMacroEventListener.class,
    DefaultWikiMacroFactory.class,
    DefaultWikiMacroManager.class,
    DefaultWikiMacro.class,
    org.xwiki.component.wiki.internal.bridge.DefaultContentParser.class,
    DefaultWikiMacroRenderer.class
})
@XWikiSyntax21ComponentList
@HTML50ComponentList
@RenderingScriptServiceComponentList
class DiscussionMacroPageTest extends PageTest
{
    private static final DocumentReference MACRO_REFERENCE =
        new DocumentReference("xwiki", List.of("Discussions", "Code"), "DiscussionMacro");

    private static final String DISCUSSION_REFERENCE = "discussionRef";

    // Needed by the rendering script service.
    @MockComponent
    private ExtendedRenderingConfiguration extendedRenderingConfiguration;

    private DiscussionsScriptService discussionsScriptService;

    @BeforeEach
    void setUp() throws Exception
    {
        this.discussionsScriptService = mock(DiscussionsScriptService.class);
        this.componentManager.registerComponent(ScriptService.class, "discussions", this.discussionsScriptService);
        DiscussionRightsScriptService rightsScriptService = mock(DiscussionRightsScriptService.class);
        when(this.discussionsScriptService.<ScriptService>get("rights")).thenReturn(rightsScriptService);
        when(rightsScriptService.canReadDiscussion(DISCUSSION_REFERENCE)).thenReturn(true);
        when(this.discussionsScriptService.getMessagesByDiscussion(any(), anyInt(), anyInt())).thenReturn(List.of());

        // The macro author is allowed to register the macro and to execute scripts.
        when(this.oldcore.getMockAuthorizationManager().hasAccess(any(Right.class), any(), any())).thenReturn(true);
        when(this.oldcore.getMockContextualAuthorizationManager().hasAccess(any(Right.class))).thenReturn(true);
        when(this.oldcore.getMockContextualAuthorizationManager().hasAccess(any(Right.class), any())).thenReturn(true);

        // Register the wiki macro, in the same way as when the macro page is saved.
        XWikiDocument macroDocument = loadPage(MACRO_REFERENCE);
        this.componentManager.registerComponent(ComponentManager.class, "wiki", this.componentManager);
        this.componentManager.<EventListener>getInstance(EventListener.class, "wikimacrolistener")
            .onEvent(new DocumentCreatedEvent(), macroDocument, null);
    }

    @Test
    void titleAndDescriptionAreDisplayedAsPlainText() throws Exception
    {
        String title = "Title ==\n\n{{html}}<p id=\"titleHtml\">html</p>{{/html}}\n\n== **Bold**";
        String description = "{{html}}<p id=\"descriptionHtml\">html</p>{{/html}} **Bold**";
        when(this.discussionsScriptService.getDiscussion(DISCUSSION_REFERENCE)).thenReturn(
            new Discussion(new DiscussionReference("test", DISCUSSION_REFERENCE), title, description, new Date(),
                null));

        XWikiDocument document =
            this.xwiki.getDocument(new DocumentReference("xwiki", "Space", "Page"), this.context);
        document.setSyntax(Syntax.XWIKI_2_1);
        document.setContent(String.format("{{discussion reference=\"%s\" namespace=\"test\" type=\"test\"/}}",
            DISCUSSION_REFERENCE));
        this.xwiki.saveDocument(document, this.context);
        this.context.setDoc(document);

        Document result = renderHTMLPage(document);

        assertNull(result.getElementById("titleHtml"));
        assertNull(result.getElementById("descriptionHtml"));
        assertNull(result.selectFirst("strong"));
        Element heading = result.selectFirst("h2");
        assertEquals("Title == {{html}}<p id=\"titleHtml\">html</p>{{/html}} == **Bold**", heading.text());
        assertEquals(description, heading.nextElementSibling().text());
    }
}
