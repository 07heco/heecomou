import logging
import re
from collections import Counter
from pathlib import Path
from typing import Optional

logger = logging.getLogger(__name__)

_CHINESE_PATTERN = re.compile(r"[\u4e00-\u9fff]+")
_SPLIT_PATTERN = re.compile(r"[，。！？；：、\n\r\t]+")


class CollocationModel:
    """基于 bigram/trigram 共现频率的中文词语搭配模型。

    通过大量正确文本学习词语之间的搭配关系，用于评估某个词在给定上下文中
    是否合理（collocation score），以及比较同音候选词的上下文适配度。
    """

    def __init__(self, window: int = 2):
        self._window = window
        self._bigram: Counter = Counter()
        self._trigram: Counter = Counter()
        self._unigram: Counter = Counter()
        self._total_bigrams = 0
        self._total_trigrams = 0

    def train(self, sentences: list[str]):
        for sentence in sentences:
            tokens = self._tokenize(sentence)
            if not tokens:
                continue
            for t in tokens:
                self._unigram[t] += 1
            self._extract_ngrams(tokens)

    def train_file(self, path: str):
        with open(path, "r", encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if line:
                    self.train([line])

    def train_corpus(self, texts: list[str], segment_func=None):
        for text in texts:
            sentences = re.split(r"[。！？\n]+", text)
            for s in sentences:
                s = s.strip()
                if not s:
                    continue
                if segment_func:
                    tokens = segment_func(s)
                else:
                    tokens = self._tokenize(s)
                if not tokens:
                    continue
                for t in tokens:
                    self._unigram[t] += 1
                self._extract_ngrams(tokens)

    def _tokenize(self, text: str) -> list[str]:
        parts = _SPLIT_PATTERN.split(text)
        tokens = []
        for part in parts:
            part = part.strip()
            if not part:
                continue
            sub_tokens = self._segment_chinese(part)
            tokens.extend(sub_tokens)
        return tokens

    def _segment_chinese(self, text: str) -> list[str]:
        segments = []
        non_chinese_parts = []
        current = ""
        for ch in text:
            if "\u4e00" <= ch <= "\u9fff":
                if current:
                    non_chinese_parts.append(current)
                    current = ""
                segments.append(ch)
            else:
                if non_chinese_parts:
                    for nc in non_chinese_parts:
                        if nc.strip():
                            segments.append(nc.strip())
                    non_chinese_parts.clear()
                current += ch
        if current and current.strip():
            segments.append(current.strip())
        return [s for s in segments if s]

    def _extract_ngrams(self, tokens: list[str]):
        n = len(tokens)
        for i in range(n - 1):
            bigram = tokens[i] + "|" + tokens[i + 1]
            self._bigram[bigram] += 1
            self._total_bigrams += 1
        for i in range(n - 2):
            trigram = tokens[i] + "|" + tokens[i + 1] + "|" + tokens[i + 2]
            self._trigram[trigram] += 1
            self._total_trigrams += 1

    def score_bigram(self, word1: str, word2: str) -> float:
        key = word1 + "|" + word2
        count = self._bigram.get(key, 0)
        if count == 0 or self._total_bigrams == 0:
            return 0.0
        unigram_count = self._unigram.get(word1, 0)
        if unigram_count == 0:
            return count / self._total_bigrams
        return count / unigram_count

    def score_context(self, word: str, context_words: list[str]) -> float:
        if not context_words:
            wc = self._unigram.get(word, 0)
            return wc / max(self._total_bigrams, 1)
        scores = []
        for ctx in context_words:
            score_fwd = self.score_bigram(ctx, word)
            score_rev = self.score_bigram(word, ctx)
            scores.append(max(score_fwd, score_rev))
        if not scores:
            return 0.0
        return sum(scores) / len(scores)

    def score_sequence(self, words: list[str]) -> float:
        if len(words) < 2:
            return 0.0
        scores = []
        for i in range(len(words) - 1):
            scores.append(self.score_bigram(words[i], words[i + 1]))
        return sum(scores) / len(scores) if scores else 0.0

    def export_dict(self) -> dict:
        return {
            "bigram": dict(self._bigram.most_common(100000)),
            "trigram": dict(self._trigram.most_common(50000)),
            "unigram": dict(self._unigram.most_common(50000)),
            "total_bigrams": self._total_bigrams,
            "total_trigrams": self._total_trigrams,
        }

    def import_dict(self, data: dict):
        self._bigram = Counter(data.get("bigram", {}))
        self._trigram = Counter(data.get("trigram", {}))
        self._unigram = Counter(data.get("unigram", {}))
        self._total_bigrams = data.get("total_bigrams", sum(self._bigram.values()))
        self._total_trigrams = data.get("total_trigrams", sum(self._trigram.values()))

    def get_stats_size(self) -> int:
        return len(self._bigram) + len(self._trigram) + len(self._unigram)

    @staticmethod
    def build_default() -> "CollocationModel":
        model = CollocationModel()
        model._load_builtin_corpus()
        return model

    def _load_builtin_corpus(self):
        corpus = _BUILTIN_CORPUS
        self.train_corpus(corpus)

    def load_correction_history(self, corrections: list[dict]):
        for entry in corrections:
            corrected = entry.get("correctedText") or entry.get("corrected_text", "")
            if corrected:
                self.train([corrected])


_BUILTIN_CORPUS = [
    "人工智能技术正在改变世界。深度学习模型广泛应用于图像识别和自然语言处理。",
    "机器学习算法需要大量训练数据。数据科学家使用Python进行数据分析。",
    "自然语言处理是人工智能的重要分支。语音识别技术取得了突破性进展。",
    "分布式系统架构可以提高系统的可靠性和可扩展性。微服务架构是目前的主流选择。",
    "云计算平台提供了弹性的计算资源。容器化技术可以简化应用的部署和管理。",
    "后端服务使用Java和Go语言开发。前端界面使用React框架构建。",
    "数据库系统需要保证数据的一致性和完整性。缓存机制可以提升系统的响应速度。",
    "网络安全是系统设计中不可忽视的重要环节。加密技术可以保护数据传输安全。",
    "敏捷开发方法可以提高团队的协作效率。持续集成和持续部署是现代开发流程。",
    "用户体验设计需要关注用户的实际需求。界面交互应该简洁直观。",
    "量子计算可能在未来改变计算范式。区块链技术在金融领域有广泛应用前景。",
    "物联网设备通过传感器收集环境数据。边缘计算可以减少数据传输延迟。",
    "软件工程强调代码质量和可维护性。单元测试是保证代码质量的重要手段。",
    "操作系统管理计算机硬件和软件资源。编译器将高级语言翻译为机器指令。",
    "计算机网络实现了全球范围内的信息交换。TCP协议提供了可靠的数据传输服务。",
    "数字经济发展迅速，数据分析成为企业决策的重要依据。智能化转型是各行业的共同趋势。",
    "创新驱动发展战略强调科技创新的核心地位。产学研合作促进科技成果转化。",
    "新能源汽车销量持续增长。自动驾驶技术逐步走向商业化应用。",
    "生物技术与信息技术深度融合。精准医疗为患者提供个性化治疗方案。",
    "教育信息化推动了教学模式的变革。在线教育平台提供丰富的学习资源。",
    "隐私计算技术可以保护数据安全。联邦学习实现了数据可用不可见的目标。",
    "图神经网络适合处理非欧几里得数据结构。强化学习在游戏和机器人控制中表现优异。",
    "大语言模型具有强大的文本生成能力。预训练加微调是当前自然语言处理的主流范式。",
    "向量数据库适合存储和检索高维嵌入向量。知识图谱可以表示实体之间的复杂关系。",
    "随着数据量的增长，数据处理成为企业发展的瓶颈。数据分析师需要理解业务需求。",
    "搜索引擎通过索引和排序算法提供相关结果。推荐系统根据用户行为进行个性化推送。",
    "智能手机已经成为人们日常生活中不可或缺的设备。移动支付改变了传统的消费方式。",
    "环境监测系统实时采集空气和水质数据。污染预警机制有助于环境保护和治理。",
    "智能交通系统可以缓解城市拥堵问题。共享单车和网约车改变了出行方式。",
    "社交媒体平台改变了人们的信息获取和社交方式。内容推荐算法影响了用户的阅读习惯。",
    "随着业务增长，系统性能面临挑战。架构优化和数据分片是常见的解决方案。",
    "高并发场景下需要考虑系统的瓶颈。异步处理和消息队列可以提升系统的吞吐量。",
    "程序员需要不断学习新技术以适应行业发展。代码审查可以及早发现潜在的问题。",
    "项目管理需要平衡时间成本和质量。风险控制是项目成功的关键因素之一。",
    "团队协作工具提高了远程办公的效率。即时通讯和视频会议缩短了沟通距离。",
    "信息检索系统帮助用户从海量数据中查找信息。全文搜索和语义搜索各有优劣。",
    "服务器运维需要监控系统的运行状态。自动化运维可以减少人工操作的错误。",
    "企业数字化转型需要技术和管理双轮驱动。数据驱动决策可以提高企业的竞争力。",
    "用户体验团队负责产品的交互设计。视觉设计需要与品牌形象保持一致。",
    "软件测试包括功能测试和性能测试。自动化测试可以提高测试的覆盖率和效率。",
    "随着用户规模的扩大，系统架构需要不断演进。容量规划和弹性伸缩是运维的重要工作。",
    "机器学习模型需要定期更新以保持性能。模型监控可以及时发现性能退化。",
    "数据仓库用于存储和分析历史数据。ETL流程负责数据的抽取转换和加载。",
    "前端性能优化包括资源压缩和懒加载。CDN加速可以减少静态资源的加载时间。",
    "后端性能优化包括数据库索引和查询优化。连接池和缓存可以降低数据库的压力。",
    "企业信息化建设需要整体规划和分步实施。信息安全体系建设是基础保障。",
    "智能客服系统利用自然语言理解技术解答用户问题。人工客服处理复杂和敏感的事务。",
    "图像识别技术广泛应用于安防和医疗领域。目标检测算法可以识别图片中的物体。",
    "语音合成技术可以将文字转换为自然流畅的语音。多模态交互是人工智能的发展方向。",
    "传统行业与互联网深度融合催生了新业态。平台经济模式改变了产业组织方式。",
    "科研成果转化需要完善的法律和制度保障。知识产权保护激励了创新主体的积极性。",
    "远程医疗打破了地域限制。在线问诊平台为患者提供了便捷的医疗服务。",
    "智慧城市建设需要整合多种信息系统。数据共享和业务协同是智慧城市的核心特征。",
    "供应链管理系统优化了企业的采购和物流流程。库存管理直接影响企业的运营成本。",
    "客户关系管理系统帮助企业维护客户资源。精准营销基于用户画像进行个性化推送。",
    "企业资源计划系统整合了企业的业务流程。财务管理模块负责企业的会计核算。",
    "人力资源管理系统管理员工的招聘和绩效。组织架构调整需要适应业务发展的需要。",
    "随着全球化的发展，跨文化沟通能力日益重要。国际化战略需要考虑各地的文化差异。",
    "技术创新和商业模式创新是企业发展的双引擎。核心竞争力决定了企业在市场中的地位。",
    "金融科技正在重塑金融服务模式。移动银行和数字钱包提供了便捷的金融服务。",
    "大数据分析可以帮助企业挖掘潜在的商业机会。数据可视化让复杂的数据变得易于理解。",
    "增强现实技术在教育领域有广泛应用前景。虚拟现实可以创造沉浸式的学习体验。",
    "人工智能伦理问题引起了社会各界的关注。算法的公平性和透明度是重要的研究方向。",
    "网络安全威胁日益复杂和多样化。零信任架构作为一种新的安全理念逐渐被业界接受。",
    "软件定义网络提高了网络管理的灵活性。网络功能虚拟化降低了网络设备的成本。",
    "边缘计算作为云计算的补充，适合处理实时性要求高的任务。端侧推理可以减少数据传输量。",
    "程序员需要关注代码的可读性和可维护性。重构是改善代码质量的有效手段。",
    "随着版本迭代的推进，代码库会逐渐积累技术债务。定期技术升级保证系统的可持续性。",
    "软件开发生命周期包括需求分析和系统设计。测试驱动开发强调先写测试再写代码。",
    "持续集成工具可以自动化代码的构建和测试。版本控制系统管理代码的历史变更。",
    "随着业务复杂度的增加，单一系统难以满足所有需求。领域驱动设计帮助团队理解业务核心。",
    "开放源代码运动推动了软件行业的创新。社区贡献使得开源项目不断完善和壮大。",
    "随着全球化进程加快，跨境电商平台提供了便捷的国际贸易服务。物流系统保障商品的快速配送。",
    "数字孪生技术在工业制造领域得到应用。通过虚拟仿真可以优化生产流程和降低试错成本。",
    "基因编辑技术为疾病治疗开辟了新路径。生命科学研究需要多学科交叉融合。",
    "能源结构调整是全球应对气候变化的重要举措。新能源技术发展促进绿色低碳转型。",
    "智能制造以数据驱动生产过程优化。工业机器人在自动化生产线中扮演关键角色。",
    "数字人民币试点范围逐步扩大。移动支付安全性受到监管机构高度关注。",
    "遥感技术可以监测地球表面的环境变化。卫星图像分析在地理信息系统中发挥重要作用。",
    "智能家居设备通过物联网技术实现互联互通。语音助手成为家庭智能控制的核心入口。",
    "内容创作平台的兴起让自媒体经济蓬勃发展。优质内容是吸引用户关注的核心要素。",
    "网络安全攻防演练可以检验系统的安全防护能力。漏洞扫描和渗透测试是安全评估的常用手段。",
    "知识管理对于企业的长期发展至关重要。经验沉淀和方法提炼可以提高团队的整体水平。",
    "随着AI技术的普及，人工智能伦理问题更加突出。我们需要平衡技术创新与社会责任。",
    "软件架构师需要具备宽广的技术视野。技术方案的选择需要权衡多方面的因素和约束条件。",
    "随着技术的发展，传统行业正在经历深刻变革。数字化转型为企业带来新的增长动力。",
    "办公自动化系统提高了日常工作的效率。电子审批流程减少了纸质文件的传递和等待时间。",
    "在线教育平台涌现出丰富的学习内容。互动式学习可以提升学生的参与度和学习效果。",
    "智慧农业利用传感器和无人机技术提高产量。精准灌溉和施肥可以节约水资源和肥料成本。",
    "文化遗产数字化保护可以延续历史记忆。三维扫描和虚拟复原技术让古迹重现光彩。",
    "新能源汽车市场竞争日益激烈。电池技术和充电设施是制约产业发展的关键因素。",
    "城市轨道交通系统缓解了路面交通压力。智慧调度系统提高了运营效率和乘客体验。",
    "网络安全意识培训有助于防范社会工程攻击。员工是信息安全防护体系的重要防线。",
    "随着企业规模的扩大，组织管理面临新的挑战。扁平化管理模式可以提升决策和响应速度。",
    "数据治理是实现数据价值最大化的基础。数据标准化和质量管理是数据治理的核心工作。",
    "微服务拆分需要合理的粒度设计。服务编排技术解决了分布式事务和一致性协调问题。",
    "日志监控系统帮助运维人员及时发现和定位问题。链路追踪技术可以分析分布式系统中的调用链。",
    "应用性能管理工具监测系统的各项性能指标。告警机制确保故障能够在第一时间得到响应。",
    "随着信息安全威胁的发展，传统防火墙已难以满足防护需求。下一代防火墙集成了更多安全检测能力。",
    "数据库读写分离可以提高系统的并发处理能力。主从复制技术实现数据的冗余备份和高可用。",
    "搜索引擎优化可以提高网站的自然流量。关键词研究和内容质量是优化的核心工作。",
    "电子政务平台提升了政府服务的效率和透明度。一网通办让市民和企业少跑腿。",
    "金融监管科技利用大数据和AI技术监测风险。反洗钱系统可以识别可疑的交易行为。",
    "精准农业利用卫星定位和大数据优化耕种方案。智能灌溉系统根据土壤湿度自动调节水量。",
    "随着人们健康意识的增强，运动健身设备市场迅速增长。可穿戴设备可以实时监测身体数据。",
]
