package com.github.pfichtner.ardulink.core.linkmanager;

import java.util.List;

import com.github.pfichtner.ardulink.core.linkmanager.LinkConfig.Named;
import com.github.pfichtner.ardulink.core.linkmanager.LinkConfig.PossibleValueFor;
import com.github.pfichtner.ardulink.core.proto.api.Protocol;
import com.github.pfichtner.ardulink.core.proto.api.Protocols;

public class DummyLinkConfig implements LinkConfig {

	public String a;
	public int b;
	@Named("c")
	public String c;
	public Protocol protocol;

	@Named("a")
	public void setPort(String a) {
		this.a = a;
	}

	@Named("b")
	public void theNameOfTheSetterDoesNotMatter(int b) {
		this.b = b;
	}

	@Named("proto")
	public void setProtocol(String protocol) {
		this.protocol = Protocols.getByName(protocol);
	}

	@PossibleValueFor("a")
	public String[] possibleValuesForAtttribute_A() {
		return new String[] { "aVal1", "aVal2" };
	}

	@PossibleValueFor("proto")
	public static String[] getProtocolsMayAlsoBeStatic() {
		List<String> names = Protocols.list();
		return names.toArray(new String[names.size()]);
	}

}