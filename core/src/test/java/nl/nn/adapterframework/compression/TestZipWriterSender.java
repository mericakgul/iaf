package nl.nn.adapterframework.compression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.InputStreamReader;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import nl.nn.adapterframework.collection.Collection;
import nl.nn.adapterframework.collection.TestCollector;
import nl.nn.adapterframework.collection.TestCollectorPart;
import nl.nn.adapterframework.parameters.Parameter;
import nl.nn.adapterframework.senders.SenderTestBase;
import nl.nn.adapterframework.stream.Message;
import nl.nn.adapterframework.util.StreamUtil;

class TestZipWriterSender extends SenderTestBase<ZipWriterSender>{

	@Override
	public ZipWriterSender createSender() throws Exception {
		ZipWriterSender zipSender = new ZipWriterSender();
		zipSender.setCollectionName("zipwriterhandle");
		return zipSender;
	}

	@BeforeEach
	void setup() {
		ZipWriter collector = new ZipWriter(false);
		Collection<ZipWriter, MessageZipEntry> collection = new Collection<>(collector);
		session.put("zipwriterhandle", collection);
	}


	@SuppressWarnings({ "rawtypes", "unchecked" })
	private Collection<TestCollector, TestCollectorPart> getCollectionFromSession() {
		Collection collection = (Collection) session.get("zipwriterhandle");
		assertNotNull(collection);
		return collection;
	}

	@Test
	void testCollectionName() throws Exception {
		sender.addParameter(new Parameter("filename","fakeFilename"));
		sender.configure();
		assertEquals("zipwriterhandle", sender.getCollectionName());
	}

	@Test
	void testChangeCollectionName() throws Exception {
		sender.addParameter(new Parameter("filename","fakeFilename"));
		sender.setZipWriterHandle("test123");
		sender.configure();
		assertEquals("test123", sender.getCollectionName());
	}

	@Test
	void testWrite() throws Exception {
		sender.addParameter(new Parameter("filename","fakeFilename"));
		sender.configure();
		sender.open();

		String fileContents = "some text to be compressed";

		sendMessage(fileContents);
		//assertEquals(fileContents, prr.getResult().asString()); // ZipWriterPipe used to return it's input

		Message result = getCollectionFromSession().build();
		try (ZipInputStream zipin = new ZipInputStream(result.asInputStream())) {
			ZipEntry entry = zipin.getNextEntry();
			assertEquals("fakeFilename", entry.getName());
			assertEquals(fileContents, StreamUtil.readerToString(StreamUtil.dontClose(new InputStreamReader(zipin)), null));
		}
	}

	@Test
	void testWriteWithContentsParameter() throws Exception {
		String fileContents = "some text to be compressed";
		String senderInput = "pipe input";
		String filename = "fakeFilename";

		sender.addParameter(new Parameter("filename",filename));
		sender.addParameter(new Parameter("contents",fileContents));
		sender.configure();
		sender.open();

		sendMessage(senderInput);

		Message result = getCollectionFromSession().build();
		try (ZipInputStream zipin = new ZipInputStream(result.asInputStream())) {
			ZipEntry entry = zipin.getNextEntry();
			assertEquals("fakeFilename", entry.getName());
			assertEquals(fileContents, StreamUtil.readerToString(StreamUtil.dontClose(new InputStreamReader(zipin)), null));
		}
	}

}
