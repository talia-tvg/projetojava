import com.fazecast.jSerialComm.SerialPort;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TinkercadSensorReader {
    // Logger para substituir printStackTrace
    private static final Logger LOGGER = Logger.getLogger(TinkercadSensorReader.class.getName());

    // Flag para controlar o loop principal
    private static volatile boolean running = true;

    // Objeto para sincronização
    private static final Object LOCK = new Object();

    // Estrutura para armazenar os dados dos sensores
    static class SensorData {
        float temperatura = 0.0f;
        int umidade = 0;
        float luminosidade = 0.0f;
        int nivelAgua = 0;

        @Override
        public String toString() {
            return String.format("Temperatura: %.1f °C, Umidade: %d%%, Luminosidade: %.0f lux, Nível de Água: %d%%",
                    temperatura, umidade, luminosidade, nivelAgua);
        }
    }

    public static void main(String[] args) {
        // Verifica se existem portas disponíveis
        SerialPort[] ports = SerialPort.getCommPorts();
        if (ports.length == 0) {
            System.out.println("Nenhuma porta serial encontrada.");
            return;
        }

        // Lista as portas disponíveis para o usuário escolher
        System.out.println("Portas seriais disponíveis:");
        for (int i = 0; i < ports.length; i++) {
            System.out.println(i + ": " + ports[i].getSystemPortName() + " - " + ports[i].getDescriptivePortName());
        }

        Scanner scanner = new Scanner(System.in);
        System.out.print("Selecione o número da porta Arduino: ");
        int portChoice = 0;
        try {
            portChoice = Integer.parseInt(scanner.nextLine());
            if (portChoice < 0 || portChoice >= ports.length) {
                System.out.println("Seleção inválida. Usando porta 0.");
                portChoice = 0;
            }
        } catch (NumberFormatException e) {
            System.out.println("Entrada inválida. Usando porta 0.");
        }

        SerialPort comPort = ports[portChoice];

        // Abre a porta serial
        if (comPort.openPort()) {
            System.out.println("Porta serial aberta: " + comPort.getSystemPortName());
        } else {
            System.out.println("Falha ao abrir a porta serial.");
            scanner.close();
            return;
        }

        try {
            // Configura a porta com a mesma taxa de baud do Arduino (9600)
            comPort.setComPortParameters(9600, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);
            // Aumentando o timeout para melhorar a estabilidade da leitura
            comPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 500, 0);

            System.out.println("Conectado ao Arduino. Lendo dados dos sensores. Pressione Enter para sair...");

            // Buffer para armazenar dados parciais entre leituras
            StringBuilder dataBuffer = new StringBuilder();

            // Objeto para armazenar os últimos dados lidos
            SensorData currentData = new SensorData();

            // Thread para monitorar entrada do usuário
            Thread inputThread = createInputThread();
            inputThread.start();

            // Aguarda um momento para o Arduino inicializar
            Thread.sleep(2000);

            // Envia um comando para iniciar a leitura dos sensores (se necessário)
            String startCommand = "START\n";
            comPort.writeBytes(startCommand.getBytes(), startCommand.length());

            // Loop para ler dados até que a thread de entrada termine
            while (running && !Thread.currentThread().isInterrupted()) {
                if (comPort.bytesAvailable() > 0) {
                    byte[] readBuffer = new byte[comPort.bytesAvailable()];
                    int numRead = comPort.readBytes(readBuffer, readBuffer.length);
                    if (numRead > 0) {
                        String data = new String(readBuffer, 0, numRead);
                        dataBuffer.append(data);

                        // Processa linhas completas
                        processCompleteLines(dataBuffer, currentData);
                    }
                }

                synchronized (LOCK) {
                    try {
                        LOCK.wait(50); // Aumentado para 50ms para reduzir uso de CPU
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erro durante a leitura", e);
        } finally {
            // Fecha a porta serial
            if (comPort.isOpen()) {
                comPort.closePort();
                System.out.println("Porta serial fechada.");
            }
            scanner.close();
        }
    }

    private static Thread createInputThread() {
        Thread inputThread = new Thread(() -> {
            Scanner consoleScanner = new Scanner(System.in);
            consoleScanner.nextLine(); // Aguarda o usuário pressionar Enter
            running = false;
            System.out.println("Finalizando programa...");
        });
        inputThread.setDaemon(true);
        return inputThread;
    }

    // Processa linhas completas e extrai dados dos sensores
    private static void processCompleteLines(StringBuilder buffer, SensorData data) {
        int newlineIndex;
        while ((newlineIndex = buffer.indexOf("\n")) >= 0) {
            // Extrai uma linha completa
            String line = buffer.substring(0, newlineIndex).trim();
            buffer.delete(0, newlineIndex + 1);

            if (!line.isEmpty()) {
                try {
                    // Verifica se a linha contém dados formatados como JSON
                    if (line.contains("{") && line.contains("}")) {
                        // Processamento de formato JSON (implementação futura)
                        System.out.println("Dados JSON recebidos: " + line);
                        continue;
                    }

                    // Analisa os dados recebidos conforme o formato do Tinkercad
                    if (line.startsWith("Temperatura:")) {
                        // Formato: "Temperatura: 25.5 °C"
                        String[] parts = line.split(":");
                        if (parts.length >= 2) {
                            String value = parts[1].trim().split(" ")[0];
                            data.temperatura = Float.parseFloat(value);
                            System.out.println("Temperatura atualizada: " + data.temperatura + " °C");
                        }
                    } else if (line.startsWith("Umidade:")) {
                        // Formato: "Umidade: 60 %"
                        String[] parts = line.split(":");
                        if (parts.length >= 2) {
                            String value = parts[1].trim().split(" ")[0];
                            data.umidade = Integer.parseInt(value);
                            System.out.println("Umidade atualizada: " + data.umidade + "%");
                        }
                    } else if (line.startsWith("Luminosidade:")) {
                        // Formato: "Luminosidade: 300 lux"
                        String[] parts = line.split(":");
                        if (parts.length >= 2) {
                            String value = parts[1].trim().split(" ")[0];
                            data.luminosidade = Float.parseFloat(value);
                            System.out.println("Luminosidade atualizada: " + data.luminosidade + " lux");
                        }
                    } else if (line.startsWith("Nivel:") || line.startsWith("Nível:")) {
                        // Formato: "Nivel: 75 %"
                        String[] parts = line.split(":");
                        if (parts.length >= 2) {
                            String value = parts[1].trim().split(" ")[0];
                            data.nivelAgua = Integer.parseInt(value);
                            System.out.println("Nível de água atualizado: " + data.nivelAgua + "%");

                            // Após receber o nível de água, temos um conjunto completo de dados
                            System.out.println("\n=== LEITURA COMPLETA DOS SENSORES ===");
                            System.out.println(data);
                            System.out.println("=====================================\n");
                        }
                    } else {
                        // Dados desconhecidos - exibir para depuração
                        System.out.println("Dados recebidos (formato desconhecido): " + line);
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Erro ao processar linha: '" + line + "'", e);
                }
            }
        }
    }
}