package io.agora.agorartcengine.external.video;

import android.opengl.EGLContext;

import io.agora.agorartcengine.external.video.gles.ProgramTextureOES;
import io.agora.agorartcengine.external.video.gles.core.EglCore;


public class GLThreadContext {
    public EglCore eglCore;
    public EGLContext context;
    public ProgramTextureOES program;
}
