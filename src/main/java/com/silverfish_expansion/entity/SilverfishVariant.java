package com.silverfish_expansion.entity;

import net.minecraft.resources.Identifier;
import com.silverfish_expansion.SilverfishExpansion;

public enum SilverfishVariant {
	ARANEO("araneo_silverfish"),
	MANTIS("mantis_silverfish"),
	FLY("fly_silverfish");

	private final String id;

	SilverfishVariant(String id) {
		this.id = id;
	}

	public String getId() {
		return id;
	}

	public Identifier modelLocation() {
		return Identifier.fromNamespaceAndPath(SilverfishExpansion.MOD_ID, id);
	}
}
