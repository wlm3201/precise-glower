package com.preciseglower.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;

public final class AlphaVertexConsumer implements VertexConsumer {
    private final VertexConsumer base;
    private final int alpha;

    public AlphaVertexConsumer(VertexConsumer base, float alpha) {
        this.base = base;
        this.alpha = Math.round(255 * Math.max(0F, Math.min(1F, alpha)));
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        return base.addVertex(x, y, z);
    }

    @Override
    public VertexConsumer setColor(int red, int green, int blue, int alpha) {
        return base.setColor(red, green, blue, this.alpha);
    }

    @Override
    public VertexConsumer setColor(int argb) {
        return base.setColor((argb & 0x00FFFFFF) | (this.alpha << 24));
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        return base.setUv(u, v);
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        return base.setUv1(u, v);
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        return base.setUv2(u, v);
    }

    @Override
    public VertexConsumer setUv3(float u, float v) {
        return base.setUv3(u, v);
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
        return base.setNormal(x, y, z);
    }

    @Override
    public VertexConsumer setLineWidth(float width) {
        return base.setLineWidth(width);
    }
}
