// Autor: Stepan Heller (helleste)

package robot;

import java.io.*;
import java.util.Arrays;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.Scanner;
import java.util.regex.*;
import java.net.*;

public class Robot {
	
	public static void main(String[] args) {
		if(args.length == 0) {
			System.out.println("Pouziti/Client: java robot <hostname> <port>");
			System.out.println("Pouziti/Server: java robot <port>");
		} else if(args.length == 1) {
			Server server = new Server(Integer.parseInt(args[0]));
			server.run();
		} else if(args.length == 2) {
			Client client = new Client(args[0], Integer.parseInt(args[1]));
			client.run();
		}
	}
}

class Server {
	
	private int port = -1;
	
	public Server(int port) {
		
		this.port = port;
		
	}
	
	public void run() {
		
		ServerSocket ssocket = null;
		
		try {
			ssocket = new ServerSocket(this.port);
		} 
		catch (IOException e) {
			System.err.println("Na portu " + this.port + " nelze poslouchat.");
			System.exit(-1);
		}
		
		System.out.println("Server spusten.");
		
		while(true) {
			
			try {
				new ClientThread(ssocket.accept()).start();
			}
			catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		//ssocket.close();
		
	}
	
}

class ClientThread extends Thread {
	
	private Socket socket = null;
	public String threadID = Long.toString(Thread.currentThread().getId());
	BufferedWriter out;
	BufferedInputStream in;
	RobotC robot;
	boolean status;
	int fprocesor = 0;
	int kroku = 0;
	
	private static final int MIN_C = -17;
	private static final int MAX_C = 17;
	private static final int MAX_E = 18;
	private static final int MIN_E = -18;
	private static final String[] COMMANDS = {"KROK", "VLEVO", "ZVEDNI", "OPRAVIT"};
	
	public ClientThread(Socket socket) {
		
		super("ClientThread");
		this.socket = socket;		
		try {
			//this.socket.setSoTimeout(3000);
			this.out = new BufferedWriter(new OutputStreamWriter(this.socket.getOutputStream()));
			this.in = new BufferedInputStream(this.socket.getInputStream());
			this.status = true;
		}
		catch(SocketTimeoutException s) {
			//end();
			
		}
		catch(IOException e) {
			//e.printStackTrace();
		}
	}
	
	public void run() {
		
		try {
			System.out.println("Prijat klient s ID: " + this.threadID);
			
			String jmeno = writeName();
			Random r = new Random();
			Coordinates pozice = new Coordinates((r.nextInt(MAX_C - MIN_C + 1) + MIN_C), 
					(r.nextInt(MAX_C - MIN_C + 1) + MIN_C));
			int otoceni = (r.nextInt(4 - 1 + 1) + 1);
			this.robot = new RobotC(jmeno, pozice, otoceni);
			
			while(status) {
				String response = getCommand();
				if(response == null) break;
				String command = parseCommand(response);
				
				if(command == "KROK") {
					step();
					continue;
				}
				
				if(command == "OPRAVIT") {
					repair(response);
					continue;
				}
				
				if(command == "VLEVO") {
					this.left();
					continue;
				}
				
				if(command == "ZVEDNI") {
					pick();
					continue;
				}
				
				// Poslan neznamy prikaz
				this.out.write("500 NEZNAMY PRIKAZ\r\n");
				this.out.flush();
			}
		}
		catch (IOException e) {
			e.printStackTrace();
		}
		
		System.out.println("Koncim vlakno.");
		end();
		return;
	}
	
	private void end() {
		
		try {
			// Uzavreni spojeni s klientem
			this.in.close();
			this.out.close();
			this.socket.close();
		} catch(IOException e) {
			e.printStackTrace();
		}
	}
	
	private int parseProcesor(String response) {
		
		Pattern p = Pattern.compile("[1-9]$");
		Matcher matcher = p.matcher(response);
		
		if(matcher.find()) return Integer.parseInt(matcher.group());
		
		return -1;
	}
	
	private void repair(String response) {
		
		try {
			int cprocesor = parseProcesor(response);
			
			if(cprocesor == -1) {
				this.out.write("500 NEZNAMY PRIKAZ\r\n");
				this.out.flush();
				return;
			}
			
			if(cprocesor == this.fprocesor) {
				this.out.write("240 OK (" + this.robot.getCoordinates().getX() + ","
										+ this.robot.getCoordinates().getY() + ")\r\n");
				this.out.flush();
				this.fprocesor = 0;
				this.kroku = 0;
				return;
			} else {
				this.out.write("571 PROCESOR FUNGUJE\r\n");
				this.out.flush();
				this.status = false;
				return;
			}
		}
		catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	// Dostali jsme prikaz krok, pokusime se ho provest
	private void step() {
		try {
			if(this.fprocesor > 0) {
				this.out.write("572 ROBOT SE ROZPADL\r\n");
				this.out.flush();
				this.status = false;
				return;
			}
	
			if(this.kroku == 8) {
				Random r = new Random();
				this.fprocesor = (r.nextInt(8) + 1);
				this.out.write("580 SELHANI PROCESORU " + fprocesor + "\r\n");
				this.out.flush();
				return;
			}
	
			this.robot.step();
	
			if(this.robot.getCoordinates().getX() > MAX_E ||
										this.robot.getCoordinates().getX() < MIN_E ||
										this.robot.getCoordinates().getY() > MAX_E ||
										this.robot.getCoordinates().getY() < MIN_E) {
								
									this.out.write("530 HAVARIE\r\n");
									this.out.flush();
									this.status = false; // Jsme mimo mesto, konec!
									return;
								}
							
			this.out.write("240 OK (" + this.robot.getCoordinates().getX() + ","
										+ this.robot.getCoordinates().getY() + ")\r\n");
			this.out.flush();
			this.kroku++;
			return;
		}
		catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	// Otocime se vlevo
	private void left() {
		try {
			this.robot.turnLeft();
			this.out.write("240 OK (" + this.robot.getCoordinates().getX() + ","
									+ this.robot.getCoordinates().getY() + ")\r\n");
			this.out.flush();
			return;
		}
		catch(IOException e) {
			e.printStackTrace();
		}
	}
	
	// Pokusime se zvednout tajemstvi
	private void pick() {
		try {
			this.status = false;
			if(this.robot.getCoordinates().getX() == 0 && this.robot.getCoordinates().getY() == 0) {
								
			this.out.write("260 USPECH Forza Viola!\r\n");
			this.out.flush();
			return;
			}
							
			// Pokusil se zvednout tajemstvi na spatne pozici
			this.out.write("550 NELZE ZVEDNOUT ZNACKU\r\n");
			this.out.flush();
			return;
		}
		catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	// Rozparsuje prichozi prikaz
	private String parseCommand(String command) {
		Pattern p = Pattern.compile(this.robot.getJmeno() + " ([A-Z]*)");
		Matcher matcher = p.matcher(command);
		
		if(!matcher.find()) return null;
		
		// Client pouzil spravne osloveni. Je spravny i prikaz?
		if(Arrays.asList(COMMANDS).contains(matcher.group(1)))
			return COMMANDS[Arrays.asList(COMMANDS).indexOf(matcher.group(1))];
		
		return null;
	}
	
	// Posle klientovi osloveni
	private String writeName() {
		
		String[][] saluts = new String[3][2];
		saluts[0][0] = "Oh Fiorentina, Di ogni squadra ti vogliam regina. Oslovuj mne Borja Valero.";
		saluts[0][1] = "Borja Valero";
		saluts[1][0] = "Rikaji mi l'Aeroplanino. Oslovuj mne Vincenzo Montella.";
		saluts[1][1] = "Vincenzo Montella";
		saluts[2][0] = "Cannelloni, Luca Toni, Pepperoni, Luca sei per me. Oslovuj mne NUMERO UNO.";
		saluts[2][1] = "NUMERO UNO";
		Random r = new Random();
		
		try {
			int r_index = r.nextInt(saluts.length);
			String osloveni = saluts[r_index][0];
			this.out.write("210 " + osloveni + "\r\n");
			this.out.flush();
			return saluts[r_index][1];
		}
		catch (IOException e) {
			e.printStackTrace();
		}
		
		return null;
	}
	
	// Vrati prikaz od klienta
	private String getCommand() {
		
//		try {
//			String command = this.scanner.next();
//			return command;
//		}
//		catch (NoSuchElementException e) {
//			this.status = false;
//			return null;
//		}
		
		StringBuilder sbuilder = new StringBuilder();
		char cur = ' ';
		String command = " ";
		int cur_int;
		
		try {
			char prev = ' ';
			
			while(true) {
				prev = cur;
				cur_int = this.in.read();
				
				if(cur_int == -1) {
					System.out.println("Nedari se cist.");
					this.status = false;
					return null;
				}
				
				cur = (char) cur_int;
				
				
				if(sbuilder.length() < 30)
					sbuilder.append(cur);
				
				if(sbuilder.length() == 30)
					sbuilder.append("\r\n");
				
				if(cur == '\n' && prev == '\r') break;
			}
		}
		catch(IOException e) {
			e.printStackTrace();
		}
		
		return command = sbuilder.toString();
	}
}

class Client {
	
	public static final int POZDRAV = 210;
	public static final int OK = 240;
	public static final int USPECH = 260;
	public static final int NEZNAMY_PRIKAZ  = 500;
	public static final int HAVARIE = 530;
	public static final int NELZE_ZVEDNOUT_ZNACKU = 550;
	public static final int PROCESOR_FUNGUJE = 571;
	public static final int ROBOT_SE_ROZPADL = 572;
	public static final int SELHANI_PROCESORU = 580;
	
	public static final int NAHORU = 1;
	public static final int DOLU = 4;
	public static final int VLEVO = 3;
	public static final int VPRAVO = 2;
	
	// Výstupní proud
	private BufferedWriter os;
	// Vstupní proud
	private BufferedReader is;
	
	private int port;
	private String host;
	private Socket socket;
	
	// Ovládaný robot
	//Robot robot;
	
	public Client(String host, int port) {
		
		this.host = host;
		this.port = port;
		
		// připojíme klienta
		connect();
	}
	
	public void connect() {
		
		// Vytvoříme adresu a socket
		InetSocketAddress addr = new InetSocketAddress(this.host, this.port);
		this.socket = new Socket();
		
		try {
			socket.connect(addr); // pokusíme se připojit
			
			// Incializujeme proudy
			this.os = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
			this.is = new BufferedReader(new InputStreamReader(socket.getInputStream()));	
		} 
		catch (SocketTimeoutException e) {
			System.out.println("Nelze se připojit k serveru.");
			System.exit(-1);
		}
		catch (UnknownHostException e) {
			System.out.println("Neznámý host.");
			System.exit(-1);
		}
		catch (IOException e) {
			System.out.println("IO chyba.");
			e.printStackTrace();
			System.exit(-1);
		}
	}
	
	public void run() {
		
		try { 
			// načteme oslovení
			while(true) {
				String osloveni = is.readLine();
				if (osloveni == null) break;
				else {
					System.out.println(osloveni);
					parseName(osloveni);
				}
			}
		}
		catch (IOException e) {
			e.printStackTrace(System.err);
		}
//		finally { // dočasné ukončení
//			close();
//		}
	}
	
	// Posli zadany prikaz serveru
	public boolean writeCommand(String command) {
		
		try {
			os.write(command);
			os.flush();
			return true;
		}
		catch(IOException e) {
			e.printStackTrace(System.err);
			return false;
		}
		
	}
	
	// Cte odpovedi
	public String getResponse() {
		
		String odpoved = "";
		try { 
			while(true) {
				odpoved = is.readLine();
				if (odpoved == null) break;
				else return odpoved;
			}
			
			return odpoved;
		}
		catch (IOException e) {
			e.printStackTrace(System.err);
			return null;
		}
		
	}
	
	// Ropozna jmeno robota
	public void parseName(String pozdrav) {
		
		Pattern p = Pattern.compile("Oslovuj mne \\S[^\r\n\0.]*\\S[.]");
		Matcher matcher = p.matcher(pozdrav);
		String osloveni = "";
		String jmeno = "";
		
		if(matcher.find()) {
			osloveni = matcher.group();
			jmeno = osloveni.substring(12, osloveni.length() - 1);
			RobotC robot = new RobotC(jmeno);
			checkPosition(robot);
		}
		else {
			return;
		}
		
	}
	
	// Zjisti kod v odpovedi
	public int parseResponse(String response) {
		
		Pattern p = Pattern.compile("^[2,5][0-9][0-9]");
		Matcher matcher = p.matcher(response);
		
		if(matcher.find()) {
			return Integer.parseInt(matcher.group());
		}
		
		System.out.println("Odpoved se nepodarilo rozlustit.");
		return 0;
		
	}
	
	// Rozpoznej cislo procesoru
	public int parseProcessor(String response) {
		
		Pattern p = Pattern.compile("[1-9]$");
		Matcher matcher = p.matcher(response);
		
		if(matcher.find()) return Integer.parseInt(matcher.group());
		
		return 0;
		
	}
	
	// Vyparsuj souradnice z odpovedi
	public Coordinates parseCoordinates(String response) {
		
		Pattern p1 = Pattern.compile("-{0,1}[0-9]{1,2},-{0,1}[0-9]{1,2}");
		Matcher m1 = p1.matcher(response);
		int x = 0;
		int y = 0;
		
		if(m1.find()) {
			String zavorka = m1.group();
			Pattern patx = Pattern.compile("-{0,1}[0-9]{1,2},");
			Pattern paty = Pattern.compile(",-{0,1}[0-9]{1,2}");
			m1 = patx.matcher(zavorka);
			if(m1.find()) {
				x = Integer.parseInt(zavorka.substring(m1.start(), m1.end() - 1));
				
				m1 = paty.matcher(zavorka);
				if(m1.find()) y = Integer.parseInt(zavorka.substring(m1.start() + 1, m1.end()));
			}
		}
		
		Coordinates coordinates = new Coordinates(x,y);
		return coordinates;
		
	}
	
	// Krok bude urcite proveden
	public String makeStep(RobotC robot) {
		robot.step(this);
		String response = getResponse();
		
		if(parseResponse(response) == SELHANI_PROCESORU) {
			System.out.println(response);
			robot.repair(this, parseProcessor(response));
			System.out.println((response = getResponse()));
			return makeStep(robot);
		}
		
		
		if(robot.getOtoceni() != -1) { // Po provedeni kroku aktualizujeme souradnice
			
			switch (robot.getOtoceni()) {
				case 1:
					robot.getCoordinates().setY(robot.getCoordinates().getY() + 1);
					break;
				case 2:
					robot.getCoordinates().setX(robot.getCoordinates().getX() + 1);
					break;
				case 3:
					robot.getCoordinates().setX(robot.getCoordinates().getX() - 1);
					break;
				case 4:
					robot.getCoordinates().setY(robot.getCoordinates().getY() -1);
					break;
	
				default:
					break;
			}
			
		}
		
		//System.out.println(response);
		return response;
	}
	
	// Zjisti uvodni souradnice
	public void checkPosition(RobotC robot) {
		
		robot.turnLeft(this);
		String pozice1 = getResponse();
		System.out.println(pozice1);
		
		String pozice2 = makeStep(robot);
		System.out.println(pozice2);
		
		// kod odpovedi je OK, v pozici 2 mam pozici po kroku
		Coordinates coord1 = parseCoordinates(pozice1);
		Coordinates coord2 = parseCoordinates(pozice2);
		
		if (coord1.getX() < coord2.getX()) robot.setOtoceni(VPRAVO);
		else if (coord1.getX() > coord2.getX()) robot.setOtoceni(VLEVO);
		else if (coord1.getY() < coord2.getY()) robot.setOtoceni(NAHORU);
		else if (coord1.getY() > coord2.getY()) robot.setOtoceni(DOLU);
		
		robot.setCoordinates(coord2);
		
		System.out.println("POZICE: " + robot.getCoordinates().getX() 
				+ ", " + robot.getCoordinates().getY() + " "
				+ "OTOCENI: " + robot.getOtoceni());
		
		findSecret(robot);
		
	}
	
	public void findSecret(RobotC robot) {
		
		String response;
		
		if(robot.getCoordinates().getY() > 0) {
			
			robot.faceDown(this);
			
			while(robot.getCoordinates().getY() != 0) {
				response = makeStep(robot);
				System.out.println(response);
			}
			
		} else if (robot.getCoordinates().getY() < 0) {
			
			robot.faceUp(this);
			
			while(robot.getCoordinates().getY() != 0) {
				response = makeStep(robot);
				System.out.println(response);
			}
		}
		
		// robot je nyni na souradnici y=0
		
		if(robot.getCoordinates().getX() > 0) {
			
			robot.faceLeft(this);
			
			while(robot.getCoordinates().getX() != 0) {
				response = makeStep(robot);
				System.out.println(response);
			}
			
		} else if (robot.getCoordinates().getX() < 0) {
			
			robot.faceRight(this);
			
			while(robot.getCoordinates().getX() != 0) {
				response = makeStep(robot);
				System.out.println(response);
			}
			
		}
		
		// robot je na pozici (0,0)
		robot.pickSecret(this);
		
	}
	
	// Uzavrit soket a odpojit streamy
	void close() {
		try {
			if(this.socket != null) this.socket.close();
			this.is.close();
			this.os.close();
		}
		catch (IOException e) {
			e.printStackTrace();
		}
		finally {
			System.exit(1);
		}
	}
}

class RobotC {
	
	private String jmeno;
	private Coordinates pozice;
	private int otoceni = -1;
	
	public RobotC(String jmeno) {
		this.jmeno = jmeno;
	}
	
	public RobotC(String jmeno, Coordinates pozice, int otoceni) {
		this.jmeno = jmeno;
		this.pozice = pozice;
		this.otoceni = otoceni;
	}
	
	public String getJmeno() {
		return this.jmeno;
	}
	
	public Coordinates getCoordinates() {
		return this.pozice;
	}
	
	public int getOtoceni() {
		return this.otoceni;
	}
	
	public void setCoordinates(Coordinates pozice) {
		this.pozice = pozice;
	}
	
	public void setOtoceni(int otoceni) {
		this.otoceni = otoceni;
	}
	
	public void turnLeft() {
		
		if(this.otoceni != -1) {
					
			switch (this.otoceni) {
				case 1:
					this.setOtoceni(3); // OTOCEN DOLEVA
					break;
				case 2:
					this.setOtoceni(1); // OTOCEN NAHORU
					break;
				case 3:
					this.setOtoceni(4); // OTOCEN DOLU
					break;
				case 4:
					this.setOtoceni(2); // OTOCEN VPRAVO
				default:
					break;
			}	
		}
	}
	
	public void turnLeft(Client master) {
		
		System.out.println(this.jmeno + " " +"VLEVO");
		master.writeCommand(this.jmeno + " " +"VLEVO" + "\r\n");
		
		if(this.otoceni != -1) {
			
			switch (this.otoceni) {
				case 1:
					this.setOtoceni(3); // OTOCEN DOLEVA
					break;
				case 2:
					this.setOtoceni(1); // OTOCEN NAHORU
					break;
				case 3:
					this.setOtoceni(4); // OTOCEN DOLU
					break;
				case 4:
					this.setOtoceni(2); // OTOCEN VPRAVO
				default:
					break;
			}
			
		}
		
	}
	
	public void step() {
		
		switch (this.otoceni) {
			case 1:
				this.pozice.setY(this.pozice.getY() + 1);
				break;
			case 2:
				this.pozice.setX(this.pozice.getX() + 1);
				break;
			case 3:
				this.pozice.setX(this.pozice.getX() - 1);
				break;
			case 4:
				this.pozice.setY(this.pozice.getY() - 1);
				break;
	
			default:
				break;
		}
	}
	
	public void step(Client master) {
		
		System.out.println(this.jmeno + " " + "KROK");
		master.writeCommand(this.jmeno + " " +"KROK" + "\r\n");
		
	}
	
	public void repair(Client master, int procesor) {
		
		System.out.println(this.jmeno + " " + "OPRAVIT" + " " + procesor);
		master.writeCommand(this.jmeno + " " + "OPRAVIT" + " " + procesor + "\r\n");
		
	}
	
	public void pickSecret(Client master) {
		
		master.writeCommand(this.jmeno + " " + "ZVEDNI" + "\r\n");
		System.out.println(this.jmeno + " " + "ZVEDNI");
		
	}
	
	// Natoc se oblicejem doleva
	public void faceLeft(Client master) {
		
		switch (this.otoceni) {
			case 1:
				this.turnLeft(master);
				System.out.println(master.getResponse());
				break;
			case 2:
				this.turnLeft(master);
				System.out.println(master.getResponse());
				this.turnLeft(master);
				System.out.println(master.getResponse());
				break;
			case 4:
				this.turnLeft(master);
				System.out.println(master.getResponse());
				this.turnLeft(master);
				System.out.println(master.getResponse());
				this.turnLeft(master);
				System.out.println(master.getResponse());
				break;
			default:
				break;
		}
		
	}
	
	// Natoc se oblicejem nahoru
	public void faceUp(Client master) {
		
		switch (this.otoceni) {
			case 2:
				this.turnLeft(master);
				System.out.println(master.getResponse());
				break;
			case 3:
				this.turnLeft(master);
				System.out.println(master.getResponse());
				this.turnLeft(master);
				System.out.println(master.getResponse());
				this.turnLeft(master);
				System.out.println(master.getResponse());
				break;
			case 4:
				this.turnLeft(master);
				System.out.println(master.getResponse());
				this.turnLeft(master);
				System.out.println(master.getResponse());
				break;
			default:
				break;
		}
		
	}
	
	// Natoc se oblicejem doprava
	public void faceRight(Client master) {
		
		switch (this.otoceni) {
			case 1:
				this.turnLeft(master);
				System.out.println(master.getResponse());
				this.turnLeft(master);
				System.out.println(master.getResponse());
				this.turnLeft(master);
				System.out.println(master.getResponse());
				break;
			case 3:
				this.turnLeft(master);
				System.out.println(master.getResponse());
				this.turnLeft(master);
				System.out.println(master.getResponse());
				break;
			case 4:
				this.turnLeft(master);
				System.out.println(master.getResponse());
				break;
			default:
				break;
		}
		
	}
	
	// Natoc se oblicejem doprava
	public void faceDown(Client master) {
		
		switch (this.otoceni) {
			case 1:
				this.turnLeft(master);
				System.out.println(master.getResponse());
				this.turnLeft(master);
				System.out.println(master.getResponse());
				break;
			case 2:
				this.turnLeft(master);
				System.out.println(master.getResponse());
				this.turnLeft(master);
				System.out.println(master.getResponse());
				this.turnLeft(master);
				System.out.println(master.getResponse());
				break;
			case 3:
				this.turnLeft(master);
				System.out.println(master.getResponse());
				break;
			default:
				break;
		}
		
	}
	
}

class Coordinates {
	
	private int x;
	private int y;
	
	public Coordinates(int x, int y) {
		
		this.x = x;
		this.y = y;
		
	}
	
	public int getX() {
		return this.x;
	}
	
	public int getY() {
		return this.y;
	}
	
	public void setX(int x) {
		this.x = x;
	}
	
	public void setY(int y) {
		this.y = y;
	}
	
}
