package comp557.a4;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

import javax.swing.JPanel;

import com.jogamp.opengl.DebugGL2;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.util.gl2.GLUT;

import mintools.parameters.BooleanParameter;
import mintools.parameters.DoubleParameter;
import mintools.swing.VerticalFlowPanel;
import mintools.viewer.EasyViewer;
import mintools.viewer.Interactor;
import mintools.viewer.SceneGraphNode;

/**
 * COMP557 - Mesh simplification application
 */
public class MeshSimplificationApp implements SceneGraphNode, Interactor {

    public static void main(String[] args) {
        new MeshSimplificationApp();
    }
    
    private PolygonSoup soup;
    
    private HEDS heds;
    
    private HalfEdge currentHE;
    
    private int whichSoup = 3;
    
    private String[] soupFiles = {
    		"meshdata/tetrahedron.obj",
    		"meshdata/topologytest.obj",
    		"meshdata/ico-sphere-tris.obj",
    		"meshdata/icosphere2.obj",
    		"meshdata/cube.obj",
    		"meshdata/cube2obj.obj",
    		"meshdata/bunny.obj",
    		"meshdata/cow.obj",    		
            "meshdata/monkey.obj",            
        };
    
    public MeshSimplificationApp() {    
        loadSoupBuildAndSubdivide( soupFiles[whichSoup] );
        EasyViewer ev = new EasyViewer("Mesh Simplification", this, new Dimension(400, 400), new Dimension(400, 400) );
        ev.addInteractor(this);
    }
    
    /**
     * Loads the currently 
     */
    private void loadSoupBuildAndSubdivide( String filename ) {          
        soup = new PolygonSoup( filename );
        heds = new HEDS( soup );
        if ( heds.faces.size() > 4 ) {
        	currentHE = heds.getBestEdgetoCollapse();
        }
        else {
        	currentHE = heds.faces.get(0).he;
        }
    }

    @Override
    public void display(GLAutoDrawable drawable) {
        GL2 gl = drawable.getGL().getGL2();

        if ( ! cullFace.getValue() ) {             
        	gl.glDisable( GL.GL_CULL_FACE );
        } else {
        	gl.glEnable( GL.GL_CULL_FACE );
        }
        if (  drawEdgeErrors.getValue() ) {  
        	heds.setRegWeight(regularizationWeight.getFloatValue());
        	heds.drawpoint(currentHE,drawable);
        } 
        
        if ( !wireFrame.getValue()) {
            // if drawing with lighting, we'll set the material
            // properties for the font and back surfaces, and set
            // polygons to render filled.
            gl.glEnable(GL2.GL_LIGHTING);
            final float frontColour[] = {.7f,.7f,0,1};
            final float backColour[] = {0,.7f,.7f,1};
            final float[] shinyColour = new float[] {1f, 1f, 1f, 1};            
            gl.glEnable(GL2.GL_LIGHTING);
            gl.glMaterialfv( GL.GL_FRONT,GL2.GL_AMBIENT_AND_DIFFUSE, frontColour, 0 );
            gl.glMaterialfv( GL.GL_BACK,GL2.GL_AMBIENT_AND_DIFFUSE, backColour, 0 );
            gl.glMaterialfv( GL.GL_FRONT_AND_BACK,GL2.GL_SPECULAR, shinyColour, 0 );
            gl.glMateriali( GL.GL_FRONT_AND_BACK,GL2.GL_SHININESS, 50 );
            gl.glLightModelf(GL2.GL_LIGHT_MODEL_TWO_SIDE, 1);
            gl.glPolygonMode( GL.GL_FRONT_AND_BACK, GL2.GL_FILL );            
        } else {
            // if drawing without lighting, we'll set the colour to white
            // and set polygons to render in wire frame
            gl.glDisable( GL2.GL_LIGHTING );
            gl.glColor4f(.7f,.7f,0.0f,1);
            gl.glPolygonMode( GL.GL_FRONT_AND_BACK, GL2.GL_LINE );
        }    
        
        
        if ( drawHEDSMesh.getValue() ) heds.display( drawable );
        
        
        if ( drawPolySoup.getValue() ) {
            gl.glDisable(GL2.GL_LIGHTING);
            gl.glEnable( GL.GL_BLEND );
            gl.glBlendFunc( GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA );
            gl.glColor4f(.7f,.7f,7.0f,0.5f);
            gl.glLineWidth(1);
            gl.glPolygonMode( GL.GL_FRONT_AND_BACK, GL2.GL_LINE );
            soup.display( drawable );
            gl.glPolygonMode( GL.GL_FRONT_AND_BACK, GL2.GL_FILL );
        }
        
        if ( drawHalfEdge.getValue() && currentHE != null ) {
            currentHE.display( drawable );
        }
        
        gl.glColor4f(1,1,1,1);
        EasyViewer.beginOverlay(drawable);
        EasyViewer.printTextLines(drawable, soupFiles[whichSoup] + " Faces = " + heds.faces.size(), 10,20,15, GLUT.BITMAP_8_BY_13 );
        EasyViewer.endOverlay(drawable);
    }
    
    @Override
    public void init(GLAutoDrawable drawable) {
        drawable.setGL( new DebugGL2(drawable.getGL().getGL2()) );
        GL2 gl = drawable.getGL().getGL2();
        gl.glEnable( GL.GL_BLEND );
        gl.glBlendFunc( GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA );
        gl.glEnable( GL.GL_LINE_SMOOTH );
        gl.glEnable( GL2.GL_POINT_SMOOTH );
        gl.glEnable( GL2.GL_NORMALIZE );
        gl.glShadeModel( GL2.GL_SMOOTH ); // Enable smooth shading, though everything should be flat!
    }

    private BooleanParameter drawPolySoup = new BooleanParameter( "draw soup mesh (wire frame)", false );    
    private BooleanParameter drawHEDSMesh = new BooleanParameter( "draw HEDS mesh", true);
    private BooleanParameter cullFace = new BooleanParameter( "cull face", true );
    private BooleanParameter wireFrame = new BooleanParameter( "wire frame", false );    
    private BooleanParameter drawHalfEdge = new BooleanParameter( "draw test half edge", true );    
    public DoubleParameter regularizationWeight = new DoubleParameter( "regularizaiton", 0.01, 1e-6, 1e2 );
    private BooleanParameter drawEdgeErrors = new BooleanParameter("draw edge errors", false );

    
    @Override
    public JPanel getControls() {
        VerticalFlowPanel vfp = new VerticalFlowPanel();
        vfp.add( drawPolySoup.getControls() );
        vfp.add( drawHEDSMesh.getControls() );                           
        vfp.add( cullFace.getControls() );
        vfp.add( wireFrame.getControls() );
        vfp.add( drawHalfEdge.getControls() );
        vfp.add( regularizationWeight.getSliderControls(true) );
        vfp.add( drawEdgeErrors.getControls() );
        return vfp.getPanel();
    }

    @Override
    public void attach(Component component) {
        component.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_SPACE) {
                    if ( currentHE.twin != null ) currentHE = currentHE.twin;                    
                } else if (e.getKeyCode() == KeyEvent.VK_N) {
                    if ( currentHE.next != null ) currentHE = currentHE.next;
                } else if ( e.getKeyCode() == KeyEvent.VK_PAGE_UP ) {
                    if ( whichSoup > 0 ) whichSoup--;                    
                    loadSoupBuildAndSubdivide( soupFiles[whichSoup] );
                } else if ( e.getKeyCode() == KeyEvent.VK_PAGE_DOWN ) {
                    if ( whichSoup < soupFiles.length -1 ) whichSoup++;                    
                    loadSoupBuildAndSubdivide( soupFiles[whichSoup] );
                } else if ( e.getKeyCode() == KeyEvent.VK_C ) {
                	heds.setRegWeight(regularizationWeight.getFloatValue());
                	currentHE = heds.edgeCollapseB(currentHE);               
                } else if ( e.getKeyCode() == KeyEvent.VK_M){
                	//Collapse 100 best edges (m for "many")
                	heds.setRegWeight(regularizationWeight.getFloatValue());
                	for(int i = 0; i<100; i++) {
                		currentHE = heds.edgeCollapseB(currentHE); 
                	}
                	
                }
                else if ( e.getKeyCode() == KeyEvent.VK_G ) {
                	heds.setRegWeight(regularizationWeight.getFloatValue());
                	if(!heds.isTetrahedron()) {
                	    currentHE = heds.getBestEdgetoCollapse();
                	}
                	else {
                		System.out.println("It's a tetrahedron!");
                	}
                }
            }
        });
    }
    
}
