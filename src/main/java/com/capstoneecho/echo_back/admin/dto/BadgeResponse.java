package com.capstoneecho.echo_back.admin.dto;

import com.capstoneecho.echo_back.statistics.badges.BadgeDef;

public record BadgeResponse(String id, String name, String condition, int threshold) {

    public static BadgeResponse from(BadgeDef def) {
        return new BadgeResponse(def.id(), def.name(), def.condition(), def.threshold());
    }
}
