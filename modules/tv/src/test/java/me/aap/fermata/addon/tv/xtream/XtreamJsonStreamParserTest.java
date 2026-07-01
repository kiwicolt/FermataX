package me.aap.fermata.addon.tv.xtream;

import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class XtreamJsonStreamParserTest extends Assert {

	@Test
	public void prepareJsonInputRejectsHtml() {
		IOException ex = assertThrows(IOException.class, () ->
				XtreamJsonStreamParser.prepareJsonInput(input("  \n<html>auth failed</html>")));

		assertTrue(ex.getMessage().contains("got HTML"));
	}

	@Test
	public void prepareJsonInputRejectsUnexpectedPayload() {
		IOException ex = assertThrows(IOException.class, () ->
				XtreamJsonStreamParser.prepareJsonInput(input("auth failed")));

		assertTrue(ex.getMessage().contains("expected JSON"));
	}

	@Test
	public void prepareJsonInputKeepsFirstJsonByteReadable() throws IOException {
		assertEquals('{', XtreamJsonStreamParser.prepareJsonInput(input("  {\"ok\":true}")).read());
		assertEquals('[', XtreamJsonStreamParser.prepareJsonInput(input("\n\t[]")).read());
	}

	private static ByteArrayInputStream input(String value) {
		return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
	}
}
