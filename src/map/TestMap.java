package map;

import java.awt.*;

import java.awt.event.*;
import java.io.*;
import java.util.*;
import javax.swing.*;
//x 10~530
//y 30~550

/*
 * Path Class:
 * used for Saving Path from
 * some positions to the HQ
 * 
 */
class Path
{
	int x,y;
	Path(){}
	Path(int x,int y){this.x=x;this.y=y;}
}

class Tank extends Thread {
	/*
	 * valid: the tank is dead? 1=not dead
	 * 
	 * x,y: position x 10~530 y 30~550
	 * 
	 * id: which tank texture to use
	 * 
	 * dir: the direction of the tank
	 * 
	 * num: the number code of the tank
	 * the player tank is 10
	 * and other tank's number > 10
	 * 
	 * speed: the moving speed of the tank
	 * 
	 * randomStep: the AI random opeartation times at the beginning 
	 * after the randomStep
	 * then the AI will select the shortest path to the HQ
	 * 
	 * nowStep:record the AI's next target position to arrive 
	 * 
	 * M:the map M
	 */
	int valid, x, y, id, dir, num, speed;
	final static int MAXSTEP=400;
	int randomStep=MAXSTEP;
	int nowStep=-1;
	
	Map M;
	
	/*
	 * pathRecord: used for recording the 
	 * AI's shortest path to the HQ
	 */
	Vector<Path>pathRecord=new Vector<Path>();
	
	/*
	 *  dx,dy: tank's moving vector in 4 directions
	 */
	
	int[] dx,dy;

	Tank(){}
	
	Tank(int x,int y,int id,int dir,Map M,int num,int speed){
		valid = 1;
		this.x = x;
		this.y = y;
		this.id = id;
		this.dir = dir;
		this.M = M;
		this.num = num;
		this.speed = speed;
		dx=new int[]{0,0,-speed,speed};
		dy=new int[]{-speed,speed,0,0};
	}
	
	public void run() {
		if (num>10)
		{
			while(! M.bStop){
				try{
					sleep(TestMap.freshTime*4);
				}catch(InterruptedException e){
					System.out.println(e);
				}
				ai_move();
			}
		}
		else
			while (! M.bStop);
	}
	
	/*
	 * keyBoard Code to Number
	 */
	int keyCodeToNum(int keyboardCode)
	{
		if (keyboardCode==KeyEvent.VK_UP)return 0;
		if (keyboardCode==KeyEvent.VK_DOWN)return 1;
		if (keyboardCode==KeyEvent.VK_LEFT)return 2;
		if (keyboardCode==KeyEvent.VK_RIGHT)return 3;
		if (keyboardCode==KeyEvent.VK_SPACE)return -2;
		return -1;
	}
	
	/*
	 * player_move
	 * receive keyboard Code to judge
	 * the operation
 	 */
	void player_move(int keyboardCode)
	{
		int dir_tmp=keyCodeToNum(keyboardCode);
		if (dir_tmp==-1)return;
		if (dir_tmp==-2)
		{
			shoot();
			return;
		}
		/*
		 * changeState:
		 * synchronized function to
		 * change the tank's x,y,dir
		 * 
		 * dir_tmp:the next move
		 */
		changeState(dir_tmp);
	}
	
	int smoothPos(int x,int y,int lastDir,int dir)
	{
		if (dir<=1)
		{
			int tmp=((x-10)/20)*20+10;
			if (lastDir==2)
			{
				if (x-tmp<=10)return tmp;
						else return tmp+20; 
			}
			if (lastDir==3)
			{
				if (x-tmp<10)return tmp;
						else return tmp+20;
			}
			return x;
		}
		else
		{
			int tmp=((y-30)/20)*20+30;
			if (lastDir==0)
			{
				if (y-tmp<=10)return tmp;
						else return tmp+20;
			}
			if (lastDir==1)
			{
				if (y-tmp<10)return tmp;
					 	else  return tmp+20;
			}
			return y;
		}
	}
	
	void ai_move()
	{
		shoot();
		if (randomStep==0)
		{
			/*
			 * random turn direction
			 */
			if (suddenTurnDirection())
			{
				pathRecord.clear();
				nowStep=-1;
				randomStep=30;
				return;
			}
			/*
			 * arrive the HQ
			 * random walk near the target
			 */
			if (M.nearHQ((x-10)/20, (y-30)/20))
			{
				pathRecord.clear();
				nowStep=-1;
				randomStep=10;
				changeState((dir+1)%4);
				return;
			}
			
			/*
			 * find the shortest path to the HQ
			 */
			if (pathRecord.isEmpty())
			{
				pathRecord=M.bfsPathToHQ((x-10)/20,(y-30)/20);
				nowStep=pathRecord.size()-1;
			}
			//printPath();
			if (nowStep==-1)
			{
				pathRecord.clear();
				randomStep=10;
				changeState((dir+1)%4);
				return;
			}
			Path tmp=pathRecord.get(nowStep);
			if (betweenTwoTarget())
			{
				nowStep--;
				//System.out.println("step "+nowStep);
				if (nowStep==-1)
				{
					pathRecord.clear();
					nowStep=-1;
					randomStep=10;
					changeState((dir+1)%4);
					return;
				}
				else
					tmp=pathRecord.get(nowStep);
			}
			int dir_tmp=whereToGo(x,y,tmp.x,tmp.y);
			if(changeState(dir_tmp));
			else
			{
				randomStep=10;
				pathRecord.clear();
				changeState((dir+1)%4);
				return;
			}
		}
		else
		{
			if (suddenTurnDirection()){randomStep--;return;}
			if (changeState(dir));
			else
			{
				Vector<Integer> move = new Vector<Integer>();
				for(int i=0;i<4;i++)
				{
					int new_x=x+dx[i];
					int new_y=y+dy[i];
					if(!out(new_x,new_y)&&canGoTo(new_x,new_y))
							move.add(i);
				}
				if (move.size()>1)move.removeElement(dir^1);
				if (move.isEmpty());
				else
				{
					int t = (int) (Math.random()*move.size());
					int u = move.elementAt(t);
					changeState(u);
				}
			}
			randomStep--;
		}
	}
	
	/*
	 * print the AI's shortest Path to HQ
	 */
	void printPath()
	{
		System.out.println("Path");
		for (int i=pathRecord.size()-1;i>=0;--i)
			System.out.println(i+" "+pathRecord.get(i).x+" "+pathRecord.get(i).y);
		System.out.println(nowStep);
		System.out.println("Coordinate: "+x+" "+y);
		//System.out.println("Map "+M.map[5][5]);
		//M.print();
	}
	
	/*
	 * AI's random movement to increase uncertainty
	 */
	boolean suddenTurnDirection()
	{
		double suddenTurnDir_a=Math.random();
		double suddenTurnDir_b=Math.random();
		if (Math.abs(suddenTurnDir_a-suddenTurnDir_b)<=0.001)
		{
			changeState((dir+1)%4);
			return true;
		}
		return false;
	}
	
	/*
	 * judge whether the AI is between two targets points
	 */
	boolean betweenTwoTarget()
	{
		if (nowStep==-1||nowStep==0)return false;
		int ux=pathRecord.get(nowStep).x,uy=pathRecord.get(nowStep).y;
		int vx=pathRecord.get(nowStep-1).x,vy=pathRecord.get(nowStep-1).y;
		if (ux==vx)
		{
			if (x!=ux)return false;
			if (uy<=y&&vy>=y)return true;
			if (uy>=y&&vy<=y)return true;
			return false;
		}
		if (uy==vy)
		{
			if (y!=uy)return false;
			if (ux<=x&&x<=vx)return true;
			if (ux>=x&&x>=vx)return true;
			return false;
		}
		return false;
	}
	
	/*
	 * shoot bullets
	 */
	void shoot()
	{
	}
	
	/*
	 * changeState:
	 * synchronized function to
	 * change the tank's x,y,dir
	 * 
	 * dir_tmp:the next move
	 */
	synchronized boolean changeState(int new_dir)
	{
		if (dir!=new_dir)
		{
			int newCoordinate=smoothPos(x,y,dir,new_dir);
			if (new_dir<=1)x=newCoordinate;
					else   y=newCoordinate;
			dir=new_dir;
			return true;
		}
		else
		{
			int new_x=x+dx[dir];
			int new_y=y+dy[dir];
			if (!out(new_x,new_y)&&canGoTo(new_x,new_y))
			{
				x=new_x;
				y=new_y;
				return true;
			}
			else return false;
		}
	}
	
	/*
	 * judge whether (x,y) is out of map
	 */
	boolean out(int x,int y)
	{
		if (x<10||x>490)return true;
		if (y<30||y>510)return true;
		return false;
	}
	
	/*
	 * judge whether tank can exist in (x,y) 
	 */
	boolean canGoTo(int x,int y){
		int new_x1,new_y1,new_x2,new_y2;
		new_x1 = (int)Math.ceil(1.0*(x-10)/20);
		new_x2 = (int)Math.floor(1.0*(x-10)/20);
		new_y1 = (int)Math.ceil(1.0*(y-30)/20);
		new_y2 = (int)Math.floor(1.0*(y-30)/20);
		//System.out.println(new_x1+"  "+new_y1+" || "+new_x2+"  "+new_y2);
		if(new_x1>=0&&new_x1+1<26&&new_y1>=0&&new_y1+1<26 && 
				new_x2>=0&&new_x2+1<26&&new_y2>=0&&new_y2+1<26){
			if(M.getMapNum(new_y1,new_x2) == 0 &&
					M.getMapNum(new_y2,new_x1) == 0 &&
					M.getMapNum(new_y1,new_x1) == 0 &&
					M.getMapNum(new_y2,new_x2) == 0 && 
					overlap(num,x,y) == false)
				return true;
		}
		return false;
	}
	
	/*
	 * judge whether two tank is not overlapped
	 */
	boolean overlap(int t,int x,int y){
		for(Tank tank : M.TankLst){
			if(tank.num != t && (Math.abs(tank.x-x)<45 && Math.abs(tank.y-y)<45))
				return true;
		}
		return false;
	}
	
	/*
	 * used for AI find the next move direction
	 * when AI already knows the shortest path from
	 * her to the HQ
	 */
	int whereToGo(int x,int y,int Targetx,int Targety)
	{
		if (x==Targetx)
		{
			if (y<Targety)return 1;
			if (y>Targety)return 0;
		}
		if (y==Targety)
		{
			if (x<Targetx)return 3;
			if (x>Targetx)return 2;
		}
		if (x<Targetx)return 3;
		if (x>Targetx)return 2;
		return -1;
	}
}

class Bullet extends Thread {
	int valid, x, y;
	Bullet(){}
	public void run() {
		
	}
}

class Map extends Frame{
	private static final long serialVersionUID = 1L;
	
	final int dx[]={0,0,-1,1};
	final int dy[]={-1,1,0,0};
	
	int map[][] = new int[26][26];
	Vector<Tank> TankLst = new Vector<Tank>();
	Vector<Bullet> BulletLst = new Vector<Bullet>();
	MainThread thread;
	int LeftTank = 1;
	boolean bStop;
	
	class MainThread extends Thread {
		public void run(){
			while(! bStop){
				repaint();
				try{
					sleep(TestMap.freshTime);
				}catch(InterruptedException e){
					System.out.println(e);
				}
			}
		}
	}
	Map(){
		try{
			FileReader in = new FileReader("Map.txt");
			BufferedReader rd = new BufferedReader(in);
			for(int i=0;i<26;i++){
				String str[] = rd.readLine().split(" ");
				for(int j=0;j<26;j++){
					map[i][j] = Integer.parseInt(str[j]);
				}
			}
			rd.close();
		}catch(IOException e){
			System.out.println(e);
		}
		this.addWindowListener(new WindowAdapter(){
			public void windowClosing(WindowEvent e){
				bStop = true;
				System.exit(0);
			}
		});
		/*
		 * keyboard listener
		 */
		this.addKeyListener(new KeyAdapter(){
			public void keyPressed(KeyEvent e)
			{
				for (Tank tank:TankLst)
				{
					if (tank.num==10)
						tank.player_move(e.getKeyCode());
				}
			}
		});
		this.setSize(650, 560);
		this.setBackground(Color.black);
		this.setVisible(true);
		thread = new MainThread();
		thread.start();
	}
	public void paint(Graphics g){
		super.paint(g);
		g.setColor(Color.white);
		g.drawLine(10, 30, 530, 30);
		g.drawLine(10, 30, 10, 550);
		g.drawLine(530, 30, 530, 550);
		g.drawLine(10, 550, 530, 550);
		String path = "pictures";
		for(int i=0;i<26;i++){
			for(int j=0;j<26;j++){
				if(map[i][j] == 1 || map[i][j] == 2){
					String dir = path + "/" + map[i][j] + ".gif";
					ImageIcon icon = new ImageIcon(dir);
					Image images = icon.getImage();
					g.drawImage(images, 10+j*20, 30+i*20, 20, 20,this);
				}
			}
		}
		String dir = path + "/"+"symbol.gif";
		ImageIcon icon = new ImageIcon(dir);
		Image images = icon.getImage();
		g.drawImage(images, 10+12*20, 30+24*20, 40, 40,this);
		paintTank(g);
		//paintBullet(g);
		//print();
	}
	void paintTank(Graphics g){
		String path = "pictures";
		for(Tank t : TankLst){
			//System.out.println(t.id + " " + t.x + " " + t.y);
			String dir = path + "/" + t.id + t.dir + ".gif";
			ImageIcon icon = new ImageIcon(dir);
			Image images = icon.getImage();
			g.drawImage(images,t.x, t.y,40, 40, this);
		}
		//System.out.println();
	}
	/*void paintBullet(Graphics g){
		for(Bullet b : BulletLst){
			
		}
	}*/
	synchronized int getMapNum(int i,int j){
		return map[i][j]+map[i+1][j]+map[i][j+1]+map[i+1][j+1];
	}
	synchronized void setMapNum(int i,int j,int t){
		map[i][j] = map[i+1][j] = map[i][j+1] = map[i+1][j+1] = t;
	}
	void print(){
		for(int i=0;i<26;i++){
			for(int j=0;j<26;j++)
				System.out.print(map[i][j] + " ");
			System.out.println();
		}
		System.out.println();
	}
	
	/*
	 * AI bfs
	 */
	Vector<Path> bfsPathToHQ(int x,int y)
	{
		int vis[][]=new int[26][26];
		Path pre[][]=new Path[26][26];
		Vector<Path>s=new Vector<Path>();
		pre[x][y]=new Path(-1,-1);
		vis[x][y]=1;
		Queue<Path>que=new LinkedList<Path>();
		que.offer(new Path(x,y));
		while (!que.isEmpty())
		{
			Path u=que.poll();
			int ux=u.x,uy=u.y;
			for (int dir=0;dir<4;++dir)
			{
				int vx=ux+dx[dir];
				int vy=uy+dy[dir];
				if (!out(vx,vy)&&canGoTo(vx,vy)&&vis[vx][vy]==0)
				{
					pre[vx][vy]=new Path(ux,uy);
					vis[vx][vy]=1;
					if (nearHQ(vx,vy))
					{
						while (vx!=x||vy!=y)
						{
							s.addElement(new Path(vx*20+10,vy*20+30));
							Path tmp=pre[vx][vy];
							vx=tmp.x;
							vy=tmp.y;
						}
						s.addElement(new Path(x*20+10,y*20+30));
						return s;
					}
					que.offer(new Path(vx,vy));
				}
			}
		}
		s.clear();
		return s;
	}
	
	/*
	 * map (x,y) is out of border?
	 */
	boolean out(int x,int y)
	{
		if (x<0||x>=25)return true;
		if (y<0||y>=25)return true;
		return false;
	}
	
	/*
	 * map[x][y] is a reachable area?
	 */
	boolean canGoTo(int x,int y)
	{
		if (map[y][x]==0&&map[y][x+1]==0&&map[y+1][x]==0&&map[y+1][x+1]==0)return true;
		return false;
	}
	
	/*
	 * map[x][y] is close to the HQ?
	 */
	boolean nearHQ(int x,int y)
	{
		if (x==9&&y==24)return true;
		if (x==15&&y==24)return true;
		if (y==21&&x==12)return true;
		return false;
	}
}

public class TestMap {
	final static int freshTime=20;
	public static void main(String[] args) {
		Map M = new Map();
		Thread th[] = new Thread[6];
		Tank tank[] = new Tank[6];
		tank[0] = new Tank(10+9*20,30+24*20,6,0,M,10,10);
		M.TankLst.add(tank[0]);
		int t = 0;
		th[t] = new Thread(tank[t]);
		th[t].start();
		t++;
		while(M.LeftTank > 0){
			M.LeftTank--;
			t++;
			tank[t] = new Tank(10+4*20,30,7,1,M,t+10,5);
			M.TankLst.add(tank[t]);
			th[t] = new Thread(tank[t]);
			th[t].start();
		}
	}
}
