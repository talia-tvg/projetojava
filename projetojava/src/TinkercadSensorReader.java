import com.fazecast.jSerialComm.SerialPort;
import java.util.Scanner;

public class TinkercadSensorReader {
    // Estrutura para armazenar os dados dos sensores
    static class SensorData {
        float temperatura = 0.0f;
        int umidade = 0;
        float luminosidade = 0.0f;
        int nivelAgua = 0;

        @Override
        public String toString() {
            return String.format("Temperatura: %.1f °C, Umidade: %d %%, Luminosidade: %.0f lux, Nível de Água: %d %%",
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
            comPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 100, 0);

            System.out.println("Lendo dados dos sensores do Tinkercad. Pressione Enter para sair...");

            // Buffer para armazenar dados parciais entre leituras
            StringBuilder dataBuffer = new StringBuilder();

            // Objeto para armazenar os últimos dados lidos
            SensorData currentData = new SensorData();

            // Cria uma thread separada para ler a entrada do usuário
            Thread inputThread = new Thread(() -> {
                try {
                    System.in.read();
                } catch (Exception e) {
                    // Ignorar exceções
                }
            });
            inputThread.setDaemon(true);
            inputThread.start();

            // Loop para ler dados até que a thread de entrada termine
            while (inputThread.isAlive() && !Thread.currentThread().isInterrupted()) {
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
                // Pequeno delay para reduzir o uso da CPU
                Thread.sleep(20);
            }
        } catch (Exception e) {
            System.err.println("Erro durante a leitura: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Fecha a porta serial
            if (comPort.isOpen()) {
                comPort.closePort();
                System.out.println("Porta serial fechada.");
            }
            scanner.close();
        }
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
                    // Analisa os dados recebidos conforme o formato do Tinkercad
                    if (line.startsWith("Temperatura:")) {
                        // Formato: "Temperatura: 25.5 °C"
                        String[] parts = line.split(" ");
                        if (parts.length >= 2) {
                            data.temperatura = Float.parseFloat(parts[1]);
                            System.out.println("Temperatura atualizada: " + data.temperatura + " °C");
                        }
                    } else if (line.startsWith("Umidade:")) {
                        // Formato: "Umidade: 60 %"
                        String[] parts = line.split(" ");
                        if (parts.length >= 2) {
                            data.umidade = Integer.parseInt(parts[1]);
                            System.out.println("Umidade atualizada: " + data.umidade + " %");
                        }
                    } else if (line.startsWith("Luminosidade:")) {
                        // Formato: "Luminosidade: 300 lux"
                        String[] parts = line.split(" ");
                        if (parts.length >= 2) {
                            data.luminosidade = Float.parseFloat(parts[1]);
                            System.out.println("Luminosidade atualizada: " + data.luminosidade + " lux");

                            // Após receber a luminosidade, temos um conjunto completo de dados
                            // Exibe o resumo completo
                            System.out.println("\n=== LEITURA COMPLETA DOS SENSORES ===");
                            System.out.println(data);
                            System.out.println("=====================================\n");
                        }
                    }

                    // Também podemos extrair o nível de água do LCD, mas isso não está sendo enviado
                    // pela porta serial no código Arduino atual

                } catch (Exception e) {
                    System.err.println("Erro ao processar linha: '" + line + "' - " + e.getMessage());
                }
            }
        }
    }
}