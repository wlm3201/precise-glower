package com.preciseglower.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;

public final class BlockOutlineData {
    private static final byte[] CORNERS = {
        0, 4, 5, 1,
        2, 3, 7, 6,
        0, 2, 3, 1,
        4, 5, 7, 6,
        0, 4, 6, 2,
        1, 3, 7, 5,
    };

    private static final int BOX_FLOATS = 6;

    public static final int MAX_FACES = 1 << 20;

    public int[] bx = new int[1024];
    public int[] by = new int[1024];
    public int[] bz = new int[1024];

    public float[] face = new float[1024 * BOX_FLOATS];

    public byte[] dir = new byte[1024];
    public int[] color = new int[1024];
    public int count;

    public int limit = Integer.MAX_VALUE;

    public void reset() {
        count = 0;
    }

    public boolean addFace(
            int x,
            int y,
            int z,
            double minX,
            double minY,
            double minZ,
            double maxX,
            double maxY,
            double maxZ,
            int direction,
            int argb) {
        if (count >= limit || count >= MAX_FACES) return false;
        if (count == bx.length) grow();
        bx[count] = x;
        by[count] = y;
        bz[count] = z;
        int p = count * BOX_FLOATS;
        face[p] = (float) minX;
        face[p + 1] = (float) minY;
        face[p + 2] = (float) minZ;
        face[p + 3] = (float) maxX;
        face[p + 4] = (float) maxY;
        face[p + 5] = (float) maxZ;
        dir[count] = (byte) direction;
        color[count] = argb;
        count++;
        return true;
    }

    private void grow() {
        int n = Math.min(bx.length * 2, MAX_FACES);
        int[] nx = new int[n];
        int[] ny = new int[n];
        int[] nz = new int[n];
        float[] nf = new float[n * BOX_FLOATS];
        byte[] nd = new byte[n];
        int[] nc = new int[n];
        System.arraycopy(bx, 0, nx, 0, bx.length);
        System.arraycopy(by, 0, ny, 0, by.length);
        System.arraycopy(bz, 0, nz, 0, bz.length);
        System.arraycopy(face, 0, nf, 0, face.length);
        System.arraycopy(dir, 0, nd, 0, dir.length);
        System.arraycopy(color, 0, nc, 0, color.length);
        bx = nx;
        by = ny;
        bz = nz;
        face = nf;
        dir = nd;
        color = nc;
    }

    public int emit(VertexConsumer consumer, int ox, int oy, int oz) {
        int vertices = 0;
        for (int i = 0; i < count; i++) {
            int p = i * BOX_FLOATS;
            float x0 = (bx[i] - ox) + face[p];
            float y0 = (by[i] - oy) + face[p + 1];
            float z0 = (bz[i] - oz) + face[p + 2];
            float x1 = (bx[i] - ox) + face[p + 3];
            float y1 = (by[i] - oy) + face[p + 4];
            float z1 = (bz[i] - oz) + face[p + 5];
            int argb = color[i];
            int base = (dir[i] & 0xFF) * 4;
            for (int v = 0; v < 4; v++) {
                int corner = CORNERS[base + v];
                consumer.addVertex(
                                (corner & 1) != 0 ? x1 : x0,
                                (corner & 2) != 0 ? y1 : y0,
                                (corner & 4) != 0 ? z1 : z0)
                        .setColor(argb);
            }
            vertices += 4;
        }
        return vertices;
    }
}
