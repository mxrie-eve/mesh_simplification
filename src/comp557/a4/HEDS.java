package comp557.a4;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.TreeMap;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;

import javax.vecmath.Matrix4d;
import javax.vecmath.Point3d;
import javax.vecmath.Vector3d;
import javax.vecmath.Vector4d;

/**
 * Half edge data structure.
 * Maintains a list of faces (i.e., one half edge of each) to allow
 * for easy display of geometry.
 */
public class HEDS {

	/** List of faces */
	List<Face> faces = new ArrayList<Face>();
	Queue<Edge> queue = new PriorityQueue<Edge>();
	double weight;

	/**
	 * Constructs an empty mesh (used when building a mesh with subdivision)
	 */
	public HEDS() {
		// do nothing
	}

	/**
	 * Builds a half edge data structure from the polygon soup   
	 * @param soup
	 */
	/**
	 * Builds a half edge data structure from the polygon soup   
	 * @param soup
	 */
	public HEDS( PolygonSoup soup ) {
		halfEdges.clear();
		faces.clear();
		for ( int[] face : soup.faceList ) {
			try {
				int i = face[face.length-1];
				int j = face[0];
				HalfEdge he = createHalfEdge( soup, i, j );
				HalfEdge first = he;                
				for ( int index = 1; index < face.length; index++ ) {
					i = j; 
					j = face[index];
					HalfEdge next = createHalfEdge( soup, i, j );
					he.next = next;
					he = next;
				}
				he.next = first;
				faces.add( new Face(he) ); // assignment is redundant, but makes it clear what's happening
			} catch ( Exception e ) {
				System.out.println("bad face, starting with... " + face[0] + " " + face[1] + " " + face[2] );
			}
		}
		buildPriorityQueue();
	}

	Map<String, HalfEdge> halfEdges = new TreeMap<>();

	private HalfEdge createHalfEdge( PolygonSoup soup, int i, int j ) throws Exception {
		String p = i+","+j;
		if ( halfEdges.containsKey( p ) ){
			throw new Exception("non orientable manifold");
		}
		String twin = j+","+i;
		HalfEdge he = new HalfEdge();
		he.head = soup.vertexList.get(j);
		he.twin = (HalfEdge) halfEdges.get( twin );
		if(he.twin == null){
			he.e = new Edge();
			he.e.he = he;
		}
		else {
			he.e = he.twin.e;
			he.twin.twin = he;
		}
		halfEdges.put( p, he );        
		return he;        
	}  
	
	public HalfEdge edgeCollapseB(HalfEdge selected_he) {
		if (!isCollapsible(selected_he)) {
			System.out.println("Edge is not collapsible");
			return selected_he;
		}
		
		System.out.println("Beginning edge collapse...");
		System.out.println("queue size: " + queue.size());



		//The half-edges adjacent to v1, excluding current one
		Set<HalfEdge> v1_inward_star = getInwardStarOfHead(selected_he);
		v1_inward_star.remove(selected_he);
		
		//Same with v2, excluding twin of current one
		Set<HalfEdge> v2_inward_star = getInwardStarOfHead(selected_he.twin);
		v2_inward_star.remove(selected_he.twin);

		Point3d new_v_position = getOptimalVPosition(selected_he);
		
		//Q of v2
		Matrix4d q_to_add = selected_he.twin.head.Q;
		
		//modifications start
		//remove all the edges to be modified from the queue. We add the apropos ones back back later
		for(HalfEdge he : v1_inward_star) {
			queue.remove(he.e);
		}
		for(HalfEdge he : v2_inward_star) {
			queue.remove(he.twin.e);
		}
		queue.remove(selected_he.e);
		
		
		//set the outside halfedges on either side of each face to be twins

		setTwins(selected_he.next.twin, selected_he.next.next.twin);
		setTwins(selected_he.twin.next.twin, selected_he.twin.next.next.twin);

		//make sure all appropriate edges are attached to the new vertex, and update Q for the new vertex and its star
		selected_he.head.Q.add(q_to_add);
		for (HalfEdge he : v2_inward_star) {
			he.head = selected_he.head;
			he.e.Q.add(q_to_add);
		}
		for (HalfEdge he : v1_inward_star) {
			he.e.Q.add(q_to_add);
		}

		//combine the inward stars of the vertices
		v1_inward_star.addAll(v2_inward_star);
		
		//move the new vertex into place. must be done before updating optimal edge collapse positions and their minimal error
		selected_he.head.p = new_v_position;
		
		//recalculate optimal edge collapse positions for star of new vertex, and then the minimal error
		for(HalfEdge he : v1_inward_star) {
			he.e.v.set(getOptimalVPosition(he));
			he.e.v.w = 1;
			he.e.error = computeEdgeError(he);
			queue.add(he.e);
		}
		queue.remove(selected_he.next.next.e);
		queue.remove(selected_he.twin.next.next.e);

		//delete the faces of the edge we started on
		//make sure that the list of faces has no duplicates! or else change this part to remove all occurences
		while(faces.contains(selected_he.leftFace) || faces.contains(selected_he.leftFace)){
			faces.remove(selected_he.leftFace);
		    faces.remove(selected_he.twin.leftFace);
		}
		System.out.println("Completed edge collapse");
		System.out.println("Edges left in queue: " + queue.size());
		return getBestEdgetoCollapse();
	}
	
	private Set<HalfEdge> getInwardStarOfHead(HalfEdge initial_he) {
		Set<HalfEdge> inward_star = new HashSet<>();
		HalfEdge he_to_add = initial_he;
		do {
			inward_star.add(he_to_add);
			he_to_add = he_to_add.next.twin;
		} while (he_to_add != initial_he);
		
		return inward_star;
	}

	public void display(GLAutoDrawable drawable) {
		GL2 gl = drawable.getGL().getGL2();

		// we do not assume triangular faces here        
		Point3d p;
		Vector3d n;
		for ( Face face : faces ) {
			HalfEdge he = face.he;
			gl.glBegin( GL2.GL_POLYGON );
			n = he.leftFace.n;
			gl.glNormal3d( n.x, n.y, n.z );
			HalfEdge e = he;
			do {
				p = e.head.p;
				gl.glVertex3d( p.x, p.y, p.z );
				e = e.next;
			} while ( e != he );
			gl.glEnd();
		}
	}   

	private HalfEdge setTwins(HalfEdge he1, HalfEdge he2){
		he1.twin = he2;
		he2.twin = he1;
		he2.e = he1.e;
		he1.e.he = he1;
		he1.twin.e.he = he1;
		return he1;
	}

	//############UTILITY FUNCTIONS

	/**
	 * Returns a list of all the incident vertex connected to the vertex
	 * of a halfedge
	 */        
	private Set<Vertex> vertexRing (HalfEdge initial_he) {
		HashSet<Vertex> set = new HashSet<>();
		HalfEdge current_he;
		current_he = initial_he;
		do{
			set.add(current_he.twin.head);
			current_he = current_he.next.twin;
		} while (!set.contains(current_he.twin.head));

		return set;
	}


	public Boolean isTetrahedron() {
		return faces.size() <= 4;
	}
	/**
	 * An edge is  not collapsable if
	 *     1. It's a tetrahedron
	 *     2. The 1-rings of the edge vertices have more than 2 vertices in common
	 */    
	public Boolean isCollapsible (HalfEdge he1) {
		// Check for tetrahedron
		if (isTetrahedron()) {
			return false;	
		}
		//Check the 1-rings
		Collection<Vertex> set1 = vertexRing(he1);
		Collection<Vertex> set2 = vertexRing(he1.twin);

		int count = 0;
		for ( Vertex v : set1){
			if (set2.contains(v)) {
				count++;
			}
		}   	
		if (count > 2 ){
			return false;	
		}
		// The edge can be collapsed
		else {
			return true;
		}
	}

	/**
	 * Set the quadric error matrix of the head vertex given a halfedge. Don't call this except on first initialization for Q of vertices.
	 */
	public Matrix4d initHeadQ (HalfEdge he1){
		Set<HalfEdge> set = getInwardStarOfHead(he1);    
		Matrix4d accumulatedQ = new Matrix4d();
		for (HalfEdge he2 : set) {
			accumulatedQ.add(he2.leftFace.K);
		} 
		he1.head.Q = accumulatedQ;
		return accumulatedQ;
	}
	
	public Matrix4d getEdgeQ (HalfEdge he1){
		Matrix4d v1Q = he1.head.Q;
		Matrix4d v2Q = he1.twin.head.Q;
		Matrix4d eQ = new Matrix4d();
		eQ.add(v1Q,v2Q);
		return eQ;
	}

	private Point3d getOptimalVPosition(HalfEdge he1) {
		Vector3d v2mv1 = new Vector3d();
		v2mv1.sub(he1.twin.head.p, he1.head.p);
		Point3d midv = new Point3d();
		midv.scaleAdd(0.5, v2mv1, he1.head.p);
		
        Matrix4d Q_of_edge = getEdgeQ(he1);
		Vector4d vposition = new Vector4d();

		//A matrix that calculates the distance to a point from another
		Matrix4d qreg =  new Matrix4d();
		qreg.setIdentity();	
		Vector4d tmpV = new Vector4d(-midv.x,-midv.y,-midv.z,midv.x*midv.x+midv.y*midv.y+midv.z*midv.z);
		qreg.setRow(3, tmpV);
		qreg.setColumn(3, tmpV);

		qreg.mul(weight,qreg);
		qreg.add(Q_of_edge);
		qreg.setRow(3, 0, 0, 0, 1);
		
		if(qreg.determinant() == 0.0) {
			return midv;
		}
		Matrix4d qinverse =  new Matrix4d();
		qinverse.invert(qreg);
		qinverse.getColumn(3, vposition);

		return new Point3d(vposition.x,vposition.y,vposition.z);
	}

	/**
	 * Set error for an edge
	 */   
	public double computeEdgeError(HalfEdge he1){
		Vector4d v = he1.e.v;
		Matrix4d tmpM = new Matrix4d();
		Vector4d tmpV = new Vector4d();
		tmpM.setColumn(0, v); //put v into tmpM as a column vector
		tmpM.mul(he1.e.Q, tmpM); //do the multiplication using Matrix4d
		tmpM.getColumn(0, tmpV); //get the result back out
		double error = v.dot(tmpV); //do the left multiplication in (v^t)Qv
		return error;
	}


	/**
	 * Set Q for all the edges and adds it to the priority queue
	 */ 
	public void buildPriorityQueue() {
		for ( Face face : faces ) {
			HalfEdge he = face.he;
			HalfEdge current_he = he;
			do {
				initHeadQ(current_he);
				initHeadQ(current_he.twin);
				current_he.e.v.set(getOptimalVPosition(current_he));
				current_he.e.v.w = 1;
				current_he.e.error = computeEdgeError(current_he);
				if (!queue.contains(current_he.e)){
					queue.add(current_he.e);
				}
				current_he = current_he.next;
			} while ( current_he != he );
		}
	}

	public HalfEdge getBestEdgetoCollapse() {
		while((!queue.isEmpty()) && (!isCollapsible(queue.peek().he))) {
			System.out.println("Top of queue is not collapsible. Removing it...");
			queue.remove();
			System.out.println("Removed top of queue. Edges remaining in queue: " + queue.size());

		}
		if(queue.isEmpty()) {
			System.out.println("Queue is empty, cannot get best candidate");
			return this.faces.get(0).he;
		}

		return queue.peek().he;
	}

	public void setRegWeight(double value){
		weight = value;
	}
	public void drawpoint( HalfEdge currentHE, GLAutoDrawable drawable) {
		Vector4d vposition = new Vector4d(getOptimalVPosition(currentHE));
		vposition.w = 1;
		GL2 gl = drawable.getGL().getGL2();
		gl.glDisable(GL2.GL_LIGHTING);
		gl.glColor4f(.0f,.7f,0.0f,1);
		gl.glPointSize(10);
		gl.glBegin(GL2.GL_POINTS);
		gl.glVertex3d(vposition.x, vposition.y,vposition.z);
		gl.glEnd();
		gl.glEnable(GL2.GL_LIGHTING);
	}
	//############END OF UTILITY FUNCTIONS
}